# Construcción en dos etapas: la primera compila con el JDK completo, la segunda solo se queda con
# el jar y un JRE. La imagen final pesa la mitad y no lleva ni el código fuente ni Maven.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app

# El wrapper y el pom van primero y solos: mientras no cambien, Docker reutiliza la capa de
# dependencias y no las vuelve a descargar en cada build.
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
# Un usuario sin privilegios: si alguien se cuela por una vulnerabilidad, no entra como root.
RUN useradd --system --no-create-home catalogo
USER catalogo

COPY --from=construccion /app/target/*.jar catalogo.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "catalogo.jar"]
