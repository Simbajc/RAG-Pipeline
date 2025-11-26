FROM eclipse-temurin:11-jre

WORKDIR /opt/app

# Copy the fat jar you built with sbt assembly
COPY modules/ingestion/target/scala-3.3.7/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar app.jar

# Optional: if you want application.conf baked into the image:
# COPY application.conf /etc/job-config/application.conf

ENTRYPOINT ["java", "-cp", "/opt/app/app.jar", "ingestion.IngestionModule"]
