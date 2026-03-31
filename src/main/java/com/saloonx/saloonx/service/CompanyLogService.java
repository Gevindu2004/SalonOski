package com.saloonx.saloonx.service;

import com.saloonx.saloonx.model.CompanyLog;
import com.saloonx.saloonx.repository.CompanyLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyLogService {

    @Autowired
    private CompanyLogRepository logRepository;

    public List<CompanyLog> getAllLogs() {
        return logRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<CompanyLog> getLogById(Long id) {
        return logRepository.findById(id);
    }

    public CompanyLog saveLog(CompanyLog log) {
        return logRepository.save(log);
    }

    public void deleteLog(Long id) {
        logRepository.deleteById(id);
    }
}
