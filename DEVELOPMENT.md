# Building Hadoop S5cmd Connector JAR

## Prerequisites

1. **Java Development Kit (JDK) 8 or later**
   - Verify with: `java -version`
   - Install on Ubuntu: `sudo apt install openjdk-8-jdk`
   - Install on CentOS/RHEL: `sudo yum install java-1.8.0-openjdk-devel`

2. **Apache Maven 3.6.0 or later**
   - Verify with: `mvn -version`
   - Install on Ubuntu: `sudo apt install maven`
   - Install on CentOS/RHEL: `sudo yum install maven`

## Step 1: Clone hadoop-s5cmd-connector repository locally

### Option 1: Clone using URL
- Follow https://github.com/git-guides/install-git to install latest Git CLI for your local development platform.
- Clone using web URL,
```
git clone https://github.com/shubhamjayawant/hadoop-s5cmd-connector.git
```

### Option 2: Clone using GitHub CLI
- Follow https://cli.github.com/ to install latest GitHub CLI for your local development platform.
- Clone using GitHub CLI
```
gh repo clone shubhamjayawant/hadoop-s5cmd-connector
```

## Step 2: Build the JAR

Build the project using Maven:

```bash
cd hadoop-s5cmd-connector/ && mvn clean package
```

After successful build, the JAR file will be available at `target/hadoop-s5cmd-connector-1.0.0.jar`.

## Step 3: Verify the JAR

Verify the contents of the JAR file:

```bash
# List the contents of the JAR
jar tvf target/hadoop-s5cmd-connector-1.0.0.jar | grep S5cmd
```

You should see the S5cmdS3AFileSystem class listed in the output.


## Troubleshooting

### Common Build Issues

1. **Compilation errors**:
   - Ensure you're using the correct Hadoop version in the pom.xml
   - Check that all required imports are available

2. **Maven dependency issues**:
   - Try running with `-U` flag: `mvn clean package -U`
   - Check your Maven settings.xml for repository configuration

3. **Java version mismatch**:
   - Ensure your JDK version is compatible with the Maven compiler plugin settings

### Testing the JAR

To test if the JAR works correctly:

1. Add it to your classpath
2. Configure Hadoop to use your custom implementation
3. Try a simple upload operation:

```bash
# Test with a simple file upload
hadoop fs -copyFromLocal /path/to/local/file s3a://your-bucket/test-file
```

4. Check the logs for messages from your custom implementation

## Customizing for Different Hadoop Versions

If you need to build for a different Hadoop version:

1. Change the `hadoop.version` property in the pom.xml
2. Rebuild the JAR

For example, for EMR 6.9.0 (which uses Hadoop 3.3.3):

```xml
<properties>
    <hadoop.version>3.3.3</hadoop.version>
    <!-- other properties -->
</properties>
```

## Additional Resources

- [Apache Hadoop Documentation](https://hadoop.apache.org/docs/current/)
- [Maven Documentation](https://maven.apache.org/guides/index.html)
- [s5cmd GitHub Repository](https://github.com/peak/s5cmd)