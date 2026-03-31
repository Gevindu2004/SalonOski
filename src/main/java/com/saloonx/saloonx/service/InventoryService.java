package com.saloonx.saloonx.service;

import com.saloonx.saloonx.model.Appointment;
import com.saloonx.saloonx.model.Product;
import com.saloonx.saloonx.model.PurchaseOrder;
import com.saloonx.saloonx.model.PurchaseOrderItem;
import com.saloonx.saloonx.model.ServiceProductRequirement;
import com.saloonx.saloonx.model.StockTransaction;
import com.saloonx.saloonx.model.Supplier;
import com.saloonx.saloonx.repository.ProductRepository;
import com.saloonx.saloonx.repository.PurchaseOrderItemRepository;
import com.saloonx.saloonx.repository.PurchaseOrderRepository;
import com.saloonx.saloonx.repository.ServiceProductRequirementRepository;
import com.saloonx.saloonx.repository.StockTransactionRepository;
import com.saloonx.saloonx.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ServiceProductRequirementRepository requirementRepository;
    private final AccountingService accountingService;

    public InventoryService(ProductRepository productRepository,
                            SupplierRepository supplierRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderItemRepository purchaseOrderItemRepository,
                            StockTransactionRepository stockTransactionRepository,
                            ServiceProductRequirementRepository requirementRepository,
                            AccountingService accountingService) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.requirementRepository = requirementRepository;
        this.accountingService = accountingService;
    }

    public Map<String, Object> getDashboardData() {
        List<Product> products = productRepository.findByActiveTrueOrderByNameAsc();
        List<Product> lowStockProducts = productRepository.findLowStockProducts();
        double totalValue = products.stream()
                .mapToDouble(p -> safe(p.getCurrentStock()) * safe(p.getUnitCost()))
                .sum();

        Map<String, Object> data = new HashMap<>();
        data.put("products", products);
        data.put("suppliers", supplierRepository.findByActiveTrueOrderByNameAsc());
        data.put("lowStockProducts", lowStockProducts);
        data.put("recentStockTransactions", stockTransactionRepository.findTop15ByOrderByCreatedAtDesc());
        data.put("recentPurchaseOrders", purchaseOrderRepository.findTop10ByOrderByCreatedAtDesc());
        data.put("serviceRequirements", requirementRepository.findAllByOrderByServiceNameAsc());
        data.put("totalProducts", products.size());
        data.put("lowStockCount", lowStockProducts.size());
        data.put("outOfStockCount", products.stream().filter(p -> safe(p.getCurrentStock()) <= 0).count());
        data.put("totalInventoryValue", totalValue);
        return data;
    }

    @Transactional
    public Product createProduct(Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku(product));
        }
        product.refreshSearchIndex();
        return productRepository.save(product);
    }

    @Transactional
    public Supplier createSupplier(Supplier supplier) {
        return supplierRepository.save(supplier);
    }

    @Transactional
    public PurchaseOrder receivePurchase(Long supplierId,
                                         Long productId,
                                         double quantity,
                                         double unitPrice,
                                         LocalDate expectedDate,
                                         String notes,
                                         String createdBy) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setPoNumber(generatePoNumber());
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setOrderDate(LocalDate.now());
        purchaseOrder.setExpectedDeliveryDate(expectedDate);
        purchaseOrder.setActualDeliveryDate(LocalDate.now());
        purchaseOrder.setStatus("RECEIVED");
        purchaseOrder.setCreatedBy(createdBy);
        purchaseOrder.setNotes(notes);
        purchaseOrder.setTotalAmount(quantity * unitPrice);
        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(purchaseOrder);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        purchaseOrderItemRepository.save(item);

        double previousStock = safe(product.getCurrentStock());
        double newStock = previousStock + quantity;
        product.setCurrentStock(newStock);
        product.setUnitCost(unitPrice);
        productRepository.save(product);

        StockTransaction stockTransaction = new StockTransaction();
        stockTransaction.setProduct(product);
        stockTransaction.setTransactionType("PURCHASE");
        stockTransaction.setQuantity(quantity);
        stockTransaction.setPreviousStock(previousStock);
        stockTransaction.setNewStock(newStock);
        stockTransaction.setReferenceType("PURCHASE_ORDER");
        stockTransaction.setReferenceId(purchaseOrder.getId());
        stockTransaction.setPerformedBy(createdBy);
        stockTransaction.setNotes(notes);
        stockTransaction.setUnitCost(unitPrice);
        stockTransactionRepository.save(stockTransaction);

        accountingService.recordInventoryPurchase(
                purchaseOrder,
                product,
                quantity * unitPrice,
                createdBy,
                "Received " + quantity + " " + product.getUnit() + " of " + product.getName());

        return purchaseOrder;
    }

    @Transactional
    public StockTransaction adjustStock(Long productId, double quantityDelta, String notes, String performedBy) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        double previousStock = safe(product.getCurrentStock());
        double newStock = previousStock + quantityDelta;
        if (newStock < 0) {
            throw new IllegalArgumentException("Adjustment would make stock negative");
        }

        product.setCurrentStock(newStock);
        productRepository.save(product);

        StockTransaction stockTransaction = new StockTransaction();
        stockTransaction.setProduct(product);
        stockTransaction.setTransactionType("ADJUSTMENT");
        stockTransaction.setQuantity(quantityDelta);
        stockTransaction.setPreviousStock(previousStock);
        stockTransaction.setNewStock(newStock);
        stockTransaction.setReferenceType("PRODUCT");
        stockTransaction.setReferenceId(product.getId());
        stockTransaction.setPerformedBy(performedBy);
        stockTransaction.setNotes(notes);
        stockTransaction.setUnitCost(product.getUnitCost());
        stockTransaction = stockTransactionRepository.save(stockTransaction);

        if (quantityDelta < 0) {
            accountingService.recordInventoryWriteOff(
                    product,
                    Math.abs(quantityDelta) * safe(product.getUnitCost()),
                    performedBy,
                    notes == null || notes.isBlank() ? "Manual stock reduction" : notes);
        }

        return stockTransaction;
    }

    @Transactional
    public ServiceProductRequirement createServiceRequirement(String serviceName,
                                                             Long productId,
                                                             double quantityUsed,
                                                             boolean mandatory) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        ServiceProductRequirement requirement = new ServiceProductRequirement();
        requirement.setServiceName(serviceName.trim());
        requirement.setProduct(product);
        requirement.setQuantityUsed(quantityUsed);
        requirement.setMandatory(mandatory);
        return requirementRepository.save(requirement);
    }

    @Transactional
    public void recordAppointmentUsage(Appointment appointment, String performedBy) {
        if (appointment == null || appointment.getService() == null || appointment.getService().isBlank()) {
            return;
        }

        List<StockTransaction> existing = stockTransactionRepository.findByReferenceTypeAndReferenceId("APPOINTMENT", appointment.getId());
        if (!existing.isEmpty()) {
            return;
        }

        List<ServiceProductRequirement> requirements = requirementRepository.findByServiceNameIgnoreCaseOrderByProductNameAsc(appointment.getService());
        for (ServiceProductRequirement requirement : requirements) {
            Product product = requirement.getProduct();
            double quantityUsed = safe(requirement.getQuantityUsed());
            double previousStock = safe(product.getCurrentStock());
            double newStock = previousStock - quantityUsed;

            if (newStock < 0) {
                throw new IllegalStateException("Insufficient stock for service " + appointment.getService() + " using product " + product.getName());
            }

            product.setCurrentStock(newStock);
            productRepository.save(product);

            StockTransaction stockTransaction = new StockTransaction();
            stockTransaction.setProduct(product);
            stockTransaction.setTransactionType("USAGE");
            stockTransaction.setQuantity(quantityUsed);
            stockTransaction.setPreviousStock(previousStock);
            stockTransaction.setNewStock(newStock);
            stockTransaction.setReferenceType("APPOINTMENT");
            stockTransaction.setReferenceId(appointment.getId());
            stockTransaction.setPerformedBy(performedBy);
            stockTransaction.setNotes("Auto-deducted for completed service: " + appointment.getService());
            stockTransaction.setUnitCost(product.getUnitCost());
            stockTransactionRepository.save(stockTransaction);

            accountingService.recordInventoryUsage(
                    appointment,
                    product,
                    quantityUsed * safe(product.getUnitCost()),
                    performedBy,
                    "Usage for appointment #" + appointment.getId());
        }
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<Supplier> getActiveSuppliers() {
        return supplierRepository.findByActiveTrueOrderByNameAsc();
    }

    private String generateSku(Product product) {
        String brand = abbreviate(product.getBrand(), 3);
        String category = abbreviate(product.getCategory(), 3);
        String name = abbreviate(product.getName(), 4);
        String base = (brand + "-" + category + "-" + name).replaceAll("[^A-Z0-9-]", "");
        if (base.replace("-", "").isBlank()) {
            base = "PRD-ITEM";
        }

        String candidate = base.toUpperCase(Locale.ROOT);
        int suffix = 1;
        while (productRepository.existsBySku(candidate)) {
            suffix++;
            candidate = base.toUpperCase(Locale.ROOT) + "-" + suffix;
        }
        return candidate;
    }

    private String generatePoNumber() {
        String prefix = "PO-" + LocalDate.now();
        int suffix = 1;
        String candidate = prefix + "-" + suffix;
        while (purchaseOrderRepository.existsByPoNumber(candidate)) {
            suffix++;
            candidate = prefix + "-" + suffix;
        }
        return candidate;
    }

    private String abbreviate(String value, int length) {
        if (value == null || value.isBlank()) {
            return "GEN";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return "GEN";
        }
        return cleaned.substring(0, Math.min(length, cleaned.length()));
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }
}
