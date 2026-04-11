# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the gradle wrappers and settings
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make the wrapper executable
RUN chmod +x gradlew

# Copy the actual application source code
COPY src src

# Build the application
RUN ./gradlew clean build -x test

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install Python 3, pip, and OpenGL libraries required for OpenCV
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python-is-python3 \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install all Machine Learning Dependencies
RUN pip3 install --no-cache-dir \
    opencv-python-headless \
    numpy \
    requests \
    torch \
    torchvision \
    pillow \
    timm \
    --break-system-packages

# Copy the built Java application
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Explicitly copy the Python ML script and PyTorch model into the root directory
COPY member05_integration.py .
COPY best_model_member04.pth .

# Expose Hugging Face Port
EXPOSE 7860

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
