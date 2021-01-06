package org.cobbzilla.s3s3mirror;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.Cleanup;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;

import java.io.*;
import java.util.List;

class TestFile {

    public static final int TEST_FILE_SIZE = 1024;

    enum Copy  { SOURCE, DEST, SOURCE_AND_DEST }
    enum Clean { SOURCE, DEST, SOURCE_AND_DEST }

    public String key;
    public File file;
    public String data;

    public TestFile() throws Exception{
        file = File.createTempFile(getClass().getName(), ".tmp");
        data = MirrorTest.random(TEST_FILE_SIZE + (RandomUtils.nextInt() % 1024));
        @Cleanup FileOutputStream out = new FileOutputStream(file);
        IOUtils.copy(new ByteArrayInputStream(data.getBytes()), out);
        file.deleteOnExit();
    }

    public static TestFile create(String key, AmazonS3Client client, List<S3Asset> stuffToCleanup, Copy copy, Clean clean) throws Exception {
        TestFile testFile = new TestFile();
        testFile.key = key;
        switch (clean) {
            case SOURCE:
                stuffToCleanup.add(new S3Asset(MirrorTest.SOURCE, key));
                break;
            case DEST:
                stuffToCleanup.add(new S3Asset(MirrorTest.DESTINATION, key));
                break;
            case SOURCE_AND_DEST:
                stuffToCleanup.add(new S3Asset(MirrorTest.SOURCE, key));
                stuffToCleanup.add(new S3Asset(MirrorTest.DESTINATION, key));
                break;
        }

        final ObjectMetadata metadata = Sha256.getS3MetadataWithHash(testFile.file);
        @Cleanup final InputStream in = new FileInputStream(testFile.file);
        switch (copy) {
            case SOURCE:
                client.putObject(MirrorTest.SOURCE, key, in, metadata);
                break;
            case DEST:
                client.putObject(MirrorTest.DESTINATION, key, in, metadata);
                break;
            case SOURCE_AND_DEST:
                client.putObject(MirrorTest.SOURCE, key, in, metadata);
                client.putObject(MirrorTest.DESTINATION, key, in, metadata);
                break;
        }
        return testFile;
    }
}
