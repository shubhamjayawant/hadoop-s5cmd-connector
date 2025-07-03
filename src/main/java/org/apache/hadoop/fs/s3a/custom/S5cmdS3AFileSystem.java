package org.apache.hadoop.fs.s3a.custom;

import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Custom S3A FileSystem implementation that uses s5cmd for uploads.
 * This implementation creates a local temporary file for write operations,
 * then uses s5cmd to upload it to S3 when the stream is closed.
 */
public class S5cmdS3AFileSystem extends S3AFileSystem {
    private static final Logger LOG = LoggerFactory.getLogger(S5cmdS3AFileSystem.class);
    
    // Configuration keys
    public static final String S5CMD_PATH = "fs.s3a.s5cmd.path";
    public static final String DEFAULT_S5CMD_PATH = "s5cmd";
    
    public static final String S5CMD_TEMP_DIR = "fs.s3a.s5cmd.temp.dir";
    // Default to system temp directory
    public static final String DEFAULT_TEMP_DIR = System.getProperty("java.io.tmpdir");
    
    public static final String S5CMD_ADDITIONAL_ARGS = "fs.s3a.s5cmd.additional.args";
    public static final String DEFAULT_ADDITIONAL_ARGS = "";
    
    public static final String S5CMD_MULTIPART_THRESHOLD = "fs.s3a.s5cmd.multipart.threshold";
    public static final long DEFAULT_MULTIPART_THRESHOLD = 100 * 1024 * 1024; // 100MB
    
    @Override
    public FSDataOutputStream create(Path f, FsPermission permission, 
                                    boolean overwrite, int bufferSize,
                                    short replication, long blockSize, 
                                    Progressable progress) throws IOException {
        if (exists(f) && !overwrite) {
            throw new IOException("File already exists: " + f);
        }
        
        return new FSDataOutputStream(
            new S5cmdOutputStream(f, getConf()),
            statistics
        );
    }
    
    /**
     * Custom OutputStream that writes to a local temp file and uploads to S3 using s5cmd on close.
     */
    private class S5cmdOutputStream extends OutputStream {
        private final Path path;
        private final File tempFile;
        private final FileOutputStream localOut;
        private final Configuration conf;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        
        public S5cmdOutputStream(Path path, Configuration conf) throws IOException {
            this.path = path;
            this.conf = conf;
            
            // Get configured temp directory
            String tempDirPath = conf.get(S5CMD_TEMP_DIR, DEFAULT_TEMP_DIR);
            File tempDir = new File(tempDirPath);
            
            // Ensure temp directory exists
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                LOG.warn("Failed to create temp directory: {}", tempDirPath);
                // Fall back to system default if we can't create the configured dir
                tempDir = null;
            }
            
            // Create a temporary file for writing
            tempFile = File.createTempFile("s5cmd-s3a-", ".tmp", tempDir);
            tempFile.deleteOnExit();
            localOut = new FileOutputStream(tempFile);
            
            LOG.info("Created temp file {} for S3 path {}", tempFile.getAbsolutePath(), path);
        }
        
        @Override
        public void write(int b) throws IOException {
            localOut.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            localOut.write(b, off, len);
        }
        
        @Override
        public void flush() throws IOException {
            localOut.flush();
        }
        
        @Override
        public void close() throws IOException {
            if (closed.getAndSet(true)) {
                return;
            }
            
            try {
                localOut.close();
                uploadToS3();
            } finally {
                if (!tempFile.delete()) {
                    LOG.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                    tempFile.deleteOnExit();
                }
            }
        }
        
        private void uploadToS3() throws IOException {
            String s3Path = path.toUri().toString();
            String s5cmdPath = conf.get(S5CMD_PATH, DEFAULT_S5CMD_PATH);
            String additionalArgs = conf.get(S5CMD_ADDITIONAL_ARGS, DEFAULT_ADDITIONAL_ARGS);
            long multipartThreshold = conf.getLong(S5CMD_MULTIPART_THRESHOLD, DEFAULT_MULTIPART_THRESHOLD);
            
            try {
                LOG.info("Uploading {} to {} using s5cmd", tempFile.getAbsolutePath(), s3Path);
                
                // Build command with base arguments
                ProcessBuilder pb;
                
                // Check if file size exceeds multipart threshold
                if (tempFile.length() > multipartThreshold) {
                    LOG.info("File size {} bytes exceeds multipart threshold of {} bytes, using multipart upload", 
                             tempFile.length(), multipartThreshold);
                    
                    // Use s5cmd cp with --concurrency for multipart uploads
                    pb = new ProcessBuilder(
                        s5cmdPath, "cp", "--concurrency", "10", tempFile.getAbsolutePath(), s3Path
                    );
                } else {
                    // Use standard cp command for smaller files
                    pb = new ProcessBuilder(
                        s5cmdPath, "cp", tempFile.getAbsolutePath(), s3Path
                    );
                }
                
                // Add any additional arguments if specified
                if (additionalArgs != null && !additionalArgs.isEmpty()) {
                    String[] args = additionalArgs.split("\\s+");
                    List<String> command = new ArrayList<>(pb.command());
                    for (String arg : args) {
                        if (!arg.trim().isEmpty()) {
                            command.add(arg.trim());
                        }
                    }
                    pb.command(command);
                }
                
                // Redirect error stream to capture any error output
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                // Capture output for logging/debugging
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    LOG.error("s5cmd output: {}", output.toString());
                    throw new IOException("s5cmd upload failed with exit code " + exitCode + ": " + output.toString());
                }
                
                LOG.info("Successfully uploaded {} to {}", tempFile.getAbsolutePath(), s3Path);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("s5cmd upload interrupted", e);
            } catch (IOException e) {
                throw new IOException("Failed to upload to S3 using s5cmd: " + e.getMessage(), e);
            }
        }
    }
}