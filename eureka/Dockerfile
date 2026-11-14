# El mismo esquema que el Dockerfile de Catálogo: se compila con el JDK y la imagen final solo
# lleva el jar y un JRE.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# curl se utiliza en el healthcheck de Docker Compose.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --no-create-home eureka
USER eureka

COPY --from=construccion /app/target/*.jar eureka.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "eureka.jar"]
