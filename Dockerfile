# Use Flink 1.19 base image with Java 17 and Scala 2.12
FROM flink:1.19.1-scala_2.12-java17

# 1) Create userlib directory for your job JAR
RUN mkdir -p /opt/flink/usrlib

# 2) Enable the S3 filesystem plugin (copy is OK as you already did)
RUN mkdir -p /opt/flink/plugins/s3-fs-hadoop \
    && cp /opt/flink/opt/flink-s3-fs-hadoop-*.jar /opt/flink/plugins/s3-fs-hadoop/

# 3) Copy your fat JAR into Flink userlib
COPY modules/ingestion/target/scala-2.12/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar \
     /opt/flink/usrlib/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar

# 4) Run the ingestion job in a local Flink mini-cluster
CMD ["bash", "-c", "java -Xms1G -Xmx2G -cp '/opt/flink/lib/*:/opt/flink/plugins/s3-fs-hadoop/*:/opt/flink/usrlib/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar' ingestion.IngestionModule"]
