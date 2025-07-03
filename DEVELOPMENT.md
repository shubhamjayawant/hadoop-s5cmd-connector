# Building the S5cmd S3A FileSystem JAR

This guide provides detailed instructions for building the custom S5cmd S3A FileSystem JAR file.

## Prerequisites

1. **Java Development Kit (JDK) 8 or later**
   - Verify with: `java -version`
   - Install on Ubuntu: `sudo apt install openjdk-8-jdk`
   - Install on CentOS/RHEL: `sudo yum install java-1.8.0-openjdk-devel`

2. **Apache Maven 3.6.0 or later**
   - Verify with: `mvn -version`
   - Install on Ubuntu: `sudo apt install maven`
   - Install on CentOS/RHEL: `sudo yum install maven`

## Step 1: Set Up Project Structure

Create the proper directory structure for the project:

```bash
# Create a new project directory
mkdir s5cmd-s3a-filesystem
cd s5cmd-s3a-filesystem

# Create the directory structure for Java files
mkdir -p src/main/java/org/apache/hadoop/fs/s3a/custom
```

## Step 2: Create Source Files

1. Create the S5cmdS3AFileSystem.java file:

```bash
# Create the main implementation file
vi src/main/java/org/apache/hadoop/fs/s3a/custom/S5cmdS3AFileSystem.java
```

Paste the S5cmdS3AFileSystem.java content into this file.

2. Create the package-info.java file:

```bash
# Create the package info file
vi src/main/java/org/apache/hadoop/fs/s3a/custom/package-info.java
```

Paste the following content:

```java
/**
 * Custom S3A FileSystem implementation that uses s5cmd for improved S3 upload performance.
 * 
 * This package contains a custom implementation of the Hadoop S3AFileSystem that
 * leverages s5cmd for S3 uploads, which can significantly improve performance
 * compared to the default AWS SDK implementation.
 * 
 * To use this implementation, configure Hadoop with:
 * 
 * fs.s3a.impl=org.apache.hadoop.fs.s3a.custom.S5cmdS3AFileSystem
 * fs.s3a.s5cmd.path=/path/to/s5cmd (optional, defaults to "s5cmd" in PATH)
 */
package org.apache.hadoop.fs.s3a.custom;
```

## Step 3: Create the Maven POM File

Create a pom.xml file in the root directory:

```bash
# Create the Maven POM file
vi pom.xml
```

Paste the following content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>s5cmd-s3a-filesystem</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>S5cmd S3A FileSystem</name>
    <description>Custom S3A FileSystem implementation using s5cmd for improved performance</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <hadoop.version>3.3.4</hadoop.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Hadoop Common -->
        <dependency>
            <groupId>org.apache.hadoop</groupId>
            <artifactId>hadoop-common</artifactId>
            <version>${hadoop.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Hadoop AWS -->
        <dependency>
            <groupId>org.apache.hadoop</groupId>
            <artifactId>hadoop-aws</artifactId>
            <version>${hadoop.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- SLF4J API -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.36</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compiler Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.10.1</version>
                <configuration>
                    <source>${maven.compiler.source}</source>
                    <target>${maven.compiler.target}</target>
                </configuration>
            </plugin>
            
            <!-- Shade Plugin to create an uber JAR with all dependencies -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Step 4: Build the JAR

Build the project using Maven:

```bash
# Build the project
mvn clean package
```

After successful build, the JAR file will be available at `target/s5cmd-s3a-filesystem-1.0.0.jar`.

## Step 5: Verify the JAR

Verify the contents of the JAR file:

```bash
# List the contents of the JAR
jar tvf target/s5cmd-s3a-filesystem-1.0.0.jar | grep S5cmd
```

You should see the S5cmdS3AFileSystem class listed in the output.

## Step 6: Deploy the JAR

Deploy the JAR file to your Hadoop environment:

### Option 1: Copy to Hadoop lib directory

```bash
cp target/s5cmd-s3a-filesystem-1.0.0.jar $HADOOP_HOME/share/hadoop/common/lib/
```

### Option 2: Add to classpath

```bash
export HADOOP_CLASSPATH=$HADOOP_CLASSPATH:/path/to/s5cmd-s3a-filesystem-1.0.0.jar
```

### Option 3: Upload to S3 for EMR

```bash
# Upload to S3 for use with EMR
aws s3 cp target/s5cmd-s3a-filesystem-1.0.0.jar s3://your-bucket/jars/
```

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