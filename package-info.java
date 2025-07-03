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