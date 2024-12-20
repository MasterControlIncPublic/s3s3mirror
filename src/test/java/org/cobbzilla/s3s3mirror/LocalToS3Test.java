package org.cobbzilla.s3s3mirror;

import com.amazonaws.services.s3.model.ObjectMetadata;
import org.junit.Test;

import java.io.File;

import static org.cobbzilla.s3s3mirror.MirrorOptions.*;
import static org.junit.Assert.assertEquals;

public class LocalToS3Test extends MirrorTestBase {

    @Test
    public void testSimpleCopy () throws Exception {
        if (!checkEnvs()) return;
        final String key = "testSimpleCopy_"+random(10);
        final String[] args = {OPT_VERBOSE, localDir.getAbsolutePath(), LOCAL_TO_S3_DESTINATION};

        testSimpleCopyInternal(args, createLocalTestFile(key));
    }

    @Test
    public void testSimpleCopyWithPrefix () throws Exception {
        if (!checkEnvs()) return;
        final String key = "testSimpleCopyWithPrefix_"+random(10);
        final String[] args = {OPT_VERBOSE, OPT_PREFIX, key, localDir.getAbsolutePath(), LOCAL_TO_S3_DESTINATION};

        final File localTestFile = createLocalTestFile(key);

        testSimpleCopyInternal(args, localTestFile);
        assertEquals(1, main.getContext().getStats().objectsCopied.get());
        assertEquals(localTestFile.length(), main.getContext().getStats().bytesUploaded.get());

        // ensure a duplicate operation does not re-upload the same content
        testSimpleCopyInternal(args, localTestFile);
        assertEquals(0, main.getContext().getStats().objectsPut.get());
        assertEquals(0, main.getContext().getStats().bytesUploaded.get());
    }

    private MirrorMain testSimpleCopyInternal(String[] args, File localFile) throws Exception {

        main = new MirrorMain(args);
        main.init();
        main.run();

        final MirrorOptions options = main.getOptions();
        final String destinationBucket = options.getDestinationBucket();
        final String destPrefix = options.getDestPrefix() == null ? "" : options.getDestPrefix();
        final String key = destPrefix + localFile.getName();
        final ObjectMetadata metadata = getS3Client().getObjectMetadata(destinationBucket, key);
        assertEquals(localFile.length(), metadata.getContentLength());
        return main;
    }

    @Test
    public void testSimpleCopyWithDestPrefix () throws Exception {
        if (!checkEnvs()) return;
        final String key = "testSimpleCopyWithDestPrefix_"+random(10);
        final String destKey = "dest_testSimpleCopyWithDestPrefix_"+random(10);
        final String[] args = {OPT_PREFIX, key, OPT_DEST_PREFIX, destKey, localDir.getAbsolutePath(), LOCAL_TO_S3_DESTINATION};
        testSimpleCopyWithDestPrefixInternal(key, destKey, args);
    }

    @Test
    public void testSimpleCopyWithInlineDestPrefix () throws Exception {
        if (!checkEnvs()) return;
        final String key = "testSimpleCopyWithInlineDestPrefix_"+random(10);
        final String destKey = "dest_testSimpleCopyWithInlineDestPrefix_"+random(10);
        final String[] args = {localDir.getAbsolutePath()+"/"+key, OPT_DEST_PREFIX, destKey, LOCAL_TO_S3_DESTINATION};
        testSimpleCopyWithDestPrefixInternal(key, destKey, args);
    }

    private void testSimpleCopyWithDestPrefixInternal(String key, String destKey, String[] args) throws Exception {
        main = new MirrorMain(args);
        main.init();

        final File testFile = createLocalTestFile(key);

        main.run();

        assertEquals(1, main.getContext().getStats().objectsCopied.get());
        assertEquals(testFile.length(), main.getContext().getStats().bytesUploaded.get());

        final ObjectMetadata metadata = getS3Client().getObjectMetadata(LOCAL_TO_S3_DESTINATION, destKey);
        assertEquals(testFile.length(), metadata.getContentLength());
    }

//    @Test
//    public void testDeleteRemoved () throws Exception {
//        if (!checkEnvs()) return;
//
//        final String key = "testDeleteRemoved_"+random(10);
//
//        main = new MirrorMain(new String[]{OPT_VERBOSE, OPT_PREFIX, key, OPT_DELETE_REMOVED, SOURCE, destination.getAbsolutePath()});
//        main.init();
//
//        // Write some files to dest
//        final int numDestFiles = 3;
//        final String[] destKeys = new String[numDestFiles];
//        final File[] destFiles = new File[numDestFiles];
//        for (int i=0; i<numDestFiles; i++) {
//            destKeys[i] = key + "-dest" + i;
//            destFiles[i] = createLocalTestFile(destKeys[i]);
//        }
//
//        // Write 1 file to localDir
//        final String srcKey = key + "-src";
//        final TestFile srcFile = createTestFile(srcKey, TestFile.Copy.SOURCE, TestFile.Clean.SOURCE_AND_DEST);
//
//        // Initiate copy
//        main.run();
//
//        // Expect only 1 copy and numDestFiles deletes
//        assertEquals(1, main.getContext().getStats().objectsCopied.get());
//        assertEquals(numDestFiles, main.getContext().getStats().objectsDeleted.get());
//
//        // Expect none of the original dest files to be there anymore
//        for (int i=0; i<numDestFiles; i++) {
//            assertFalse("testDeleteRemoved: expected " + destFiles[i] + " to be removed", destFiles[i].exists());
//        }
//
//        // Expect localDir file to now be present in both localDir bucket and destination directory
//        final ObjectMetadata metadata = getS3Client().getObjectMetadata(SOURCE, srcKey);
//        assertEquals(srcFile.data.length(), metadata.getContentLength());
//        assertEquals(new File(destination.getAbsolutePath() + File.separator + srcKey).length(), metadata.getContentLength());
//    }

}
