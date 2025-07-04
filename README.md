# hadoop-s5cmd-connector
A high-performance Hadoop connector that overrides S3A FileSystem operations to leverage [s5cmd](https://github.com/peak/s5cmd) for dramatically faster S3 uploads and downloads compared to the default AWS SDK implementation. Seamless integration for Hadoop, Spark, and Hive workloads with minimal configuration changes.

## Prerequisites

1. Install s5cmd on all nodes that will perform S3 operations:
   ```bash
   # For Linux (64-bit)
   curl -L https://github.com/peak/s5cmd/releases/latest/download/s5cmd_Linux_x86_64.tar.gz | tar -xz
   sudo mv s5cmd /usr/local/bin/
   ```

2. Ensure s5cmd is properly configured with AWS credentials (via environment variables, AWS profile, or instance profile)

## Installation

### Building the JAR

Follow steps described in [DEVELOPMENT.md](DEVELOPMENT.md)

### Adding the JAR to Hadoop

Add the JAR to Hadoop's classpath using one of these methods:

1. **Copy to Hadoop lib directory**:
   ```bash
   cp target/hadoop-s5cmd-connector-1.0.0.jar $HADOOP_HOME/share/hadoop/common/lib/
   ```

2. **Using the classpath command**:
   ```bash
   export HADOOP_CLASSPATH=$HADOOP_CLASSPATH:/path/to/hadoop-s5cmd-connector-1.0.0.jar
   ```

3. **Add to HDFS and use with -libjars**:
   ```bash
   hdfs dfs -put target/hadoop-s5cmd-connector-1.0.0.jar /user/hadoop/lib/
   hadoop jar your-application.jar -libjars hdfs:///user/hadoop/lib/hadoop-s5cmd-connector-1.0.0.jar
   ```

## Configuration

Add the following to your Hadoop configuration (core-site.xml or hdfs-site.xml):

```xml
<!-- Use the custom S3A FileSystem implementation -->
<property>
  <name>fs.s3a.impl</name>
  <value>org.apache.hadoop.fs.s3a.custom.S5cmdS3AFileSystem</value>
</property>

<!-- Optional: Path to s5cmd executable (defaults to "s5cmd" in PATH) -->
<property>
  <name>fs.s3a.s5cmd.path</name>
  <value>/usr/local/bin/s5cmd</value>
</property>

<!-- Optional: Directory for temporary files (defaults to system temp directory) -->
<property>
  <name>fs.s3a.s5cmd.temp.dir</name>
  <value>/path/to/temp/directory</value>
</property>

<!-- Optional: Additional arguments to pass to s5cmd -->
<property>
  <name>fs.s3a.s5cmd.additional.args</name>
  <value>--no-verify-ssl --endpoint-url https://custom-endpoint.example.com</value>
</property>

<!-- Optional: Threshold in bytes for using multipart uploads (defaults to 100MB) -->
<property>
  <name>fs.s3a.s5cmd.multipart.threshold</name>
  <value>104857600</value>
</property>
```

## Usage

Once configured, any Hadoop operations that write to S3 will automatically use s5cmd for uploads:

```bash
# Example: Copy a file to S3
hadoop fs -cp /local/path/file s3a://bucket/path/

# Example: Run a MapReduce job with S3 output
hadoop jar my-job.jar MyJob -Dfs.s3a.impl=org.apache.hadoop.fs.s3a.custom.S5cmdS3AFileSystem \
  s3a://input-bucket/data/ s3a://output-bucket/results/
```

## AWS EMR Integration

To use this custom S3A FileSystem implementation on AWS EMR, follow these steps:

### 1. Create a Bootstrap Action Script

Create a bootstrap action script to install s5cmd and deploy your custom JAR:

```bash
#!/bin/bash
# File: s5cmd-bootstrap.sh

# Install s5cmd on all nodes
sudo curl -L https://github.com/peak/s5cmd/releases/latest/download/s5cmd_Linux_x86_64.tar.gz | sudo tar -xz -C /tmp/
sudo mv /tmp/s5cmd /usr/local/bin/
sudo chmod +x /usr/local/bin/s5cmd

# Download the custom S3AFileSystem JAR from S3
sudo aws s3 cp s3://your-bucket/path/to/hadoop-s5cmd-connector.jar /usr/lib/hadoop/lib/

# Create a directory for temporary files with appropriate permissions
sudo mkdir -p /mnt/s5cmd-tmp
sudo chmod 777 /mnt/s5cmd-tmp
```

### 2. Create EMR Configuration JSON

Create a configuration JSON file to configure Hadoop to use your custom implementation:

```json
[
  {
    "Classification": "core-site",
    "Properties": {
      "fs.s3a.impl": "org.apache.hadoop.fs.s3a.custom.S5cmdS3AFileSystem",
      "fs.s3a.s5cmd.path": "/usr/local/bin/s5cmd",
      "fs.s3a.s5cmd.temp.dir": "/mnt/s5cmd-tmp",
      "fs.s3a.s5cmd.multipart.threshold": "104857600"
    }
  }
]
```

### 3. Launch EMR Cluster with Custom Configuration

#### Using AWS CLI:

```bash
aws emr create-cluster \
  --name "EMR with s5cmd S3A FileSystem" \
  --release-label emr-6.9.0 \
  --applications Name=Hadoop Name=Spark \
  --ec2-attributes KeyName=your-key \
  --instance-type m5.xlarge \
  --instance-count 3 \
  --bootstrap-actions Path=s3://your-bucket/path/to/s5cmd-bootstrap.sh \
  --configurations file:///path/to/your/configuration.json
```

#### Using AWS Console:

1. Upload your bootstrap script and configuration JSON to an S3 bucket
2. When creating an EMR cluster, add a bootstrap action pointing to your script
3. In the "Software Configuration" section, select "Edit software settings" and provide the S3 path to your configuration JSON

### 4. Verify Installation

After your cluster is running, SSH into the master node and verify the installation:

```bash
# Check if s5cmd is installed
s5cmd version

# Verify the custom JAR is in the classpath
ls -la /usr/lib/hadoop/lib/hadoop-s5cmd-connector.jar

# Test with a simple upload
hadoop fs -copyFromLocal /etc/hosts s3a://your-bucket/test-file
```

### 5. EMR Step Configuration

When submitting jobs as steps, you can also specify the custom implementation:

```bash
aws emr add-steps \
  --cluster-id j-XXXXXXXXXXXXX \
  --steps Type=Spark,Name="Spark Job with s5cmd",ActionOnFailure=CONTINUE,Args=[--conf,fs.s3a.impl=org.apache.hadoop.fs.s3a.custom.S5cmdS3AFileSystem,--class,com.example.SparkJob,s3a://your-bucket/path/to/your-jar.jar]
```

### 6. Performance Tuning for EMR

For optimal performance on EMR:

- Use instance storage for temporary files by setting `fs.s3a.s5cmd.temp.dir` to `/mnt/s5cmd-tmp`
- Consider increasing the multipart threshold for large files
- For Spark jobs, adjust executor memory and cores based on your workload
- Use EMR instance types with enhanced networking for better S3 throughput

## How It Works

This implementation:
1. Creates a local temporary file for write operations
2. Writes data to the temporary file
3. When the stream is closed, uploads the file to S3 using s5cmd
   - For files larger than the multipart threshold, uses s5cmd's concurrency feature
   - Applies any additional configured arguments to s5cmd
4. Deletes the temporary file after upload

## Limitations

- Currently only the upload (write) operations are optimized with s5cmd
- For very large files, ensure sufficient local disk space for temporary files
- Error handling may differ from the standard S3AFileSystem implementation

## Performance Considerations

- s5cmd is significantly faster than the AWS SDK for large file uploads
- For optimal performance, consider:
  - Setting an appropriate multipart threshold based on your file sizes
  - Using a fast local disk for temporary files
  - Adjusting concurrency settings via additional arguments for your specific environment
  - Ensuring network bandwidth between Hadoop nodes and S3 is sufficient

## Troubleshooting

If you encounter issues:

1. Check s5cmd logs in Hadoop logs
2. Verify s5cmd is properly installed and accessible
3. Ensure AWS credentials are properly configured
4. Try running s5cmd manually with the same parameters to isolate issues

### EMR-Specific Troubleshooting

1. Check bootstrap action logs in `/var/log/bootstrap-actions/`
2. Verify EMR IAM roles have appropriate S3 permissions
3. For cluster-wide issues, check EMR step logs and Hadoop logs
4. Use EMR debugging options when creating the cluster for more detailed logs