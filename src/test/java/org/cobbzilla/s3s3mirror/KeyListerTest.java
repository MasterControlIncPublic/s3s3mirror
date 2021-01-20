package org.cobbzilla.s3s3mirror;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class KeyListerTest {
    private static final String[] PREFIXES = {"prefixa", "prefixb"};
    private static final int COUNT_PER_PREFIX = 10;

    @Test
    public void testNoExclusionFilterInitialLoad() {
        KeyLister lister = getLister();

        List<S3ObjectSummary> result = lister.getNextBatch();

        assertEquals(20, result.size());
    }

    @Test
    public void testNoExclusionFilterSecondaryLoads() {
        KeyLister lister = getLister();

        lister.getNextBatch();

        // Making sure results are empty
        assertEquals(0, lister.getNextBatch().size());

        lister.run();

        List<S3ObjectSummary> result = lister.getNextBatch();

        assertEquals(20, result.size());
    }

    @Test
    public void testExclusionFilterInitialLoad() {
        KeyLister lister = getLister(PREFIXES[0]);

        List<S3ObjectSummary> result = lister.getNextBatch();

        assertEquals(10, result.size());
        assertFalse(result.stream().anyMatch(summary -> summary.getKey().startsWith(PREFIXES[0])));
    }

    @Test
    public void testExclusionFilterSecondaryLoads() {
        KeyLister lister = getLister(PREFIXES[0]);

        lister.getNextBatch();

        // Making sure results are empty
        assertEquals(0, lister.getNextBatch().size());

        lister.run();

        List<S3ObjectSummary> result = lister.getNextBatch();

        assertEquals(10, result.size());
        assertFalse(result.stream().anyMatch(summary -> summary.getKey().startsWith(PREFIXES[0])));
    }

    private static KeyLister getLister() {
        return getLister("");
    }

    private static KeyLister getLister(String exclusionFilter) {
        AmazonS3Client client = mock(AmazonS3Client.class);
        ObjectListing mockListing1 = mock(ObjectListing.class);
        ObjectListing mockListing2 = mock(ObjectListing.class);
        List<S3ObjectSummary> testSummaries = new ArrayList<>();
        for (String prefix : PREFIXES) {
            testSummaries.addAll(generateS3ObjectSummariesWithPrefix(prefix, COUNT_PER_PREFIX));
        }

        doReturn(testSummaries).when(mockListing1).getObjectSummaries();
        doReturn(true).when(mockListing1).isTruncated();
        doReturn(testSummaries).when(mockListing2).getObjectSummaries();
        doReturn(mockListing1).when(client).listObjects(any(ListObjectsRequest.class));
        doReturn(mockListing2).when(client).listNextBatchOfObjects(any(ObjectListing.class));

        Owner owner = new Owner();
        MirrorOptions options = new MirrorOptions();
        options.setExcludePrefix(exclusionFilter);
        MirrorContext context = new MirrorContext(options, owner);

        return new KeyLister(client, context, PREFIXES.length * COUNT_PER_PREFIX * 2, "testbucket", "");
    }

    private static List<S3ObjectSummary> generateS3ObjectSummariesWithPrefix(String prefix, int count) {
        List<S3ObjectSummary> summaries = new ArrayList<>();

        for (int i=0; i < count; i++) {
            S3ObjectSummary summary = new S3ObjectSummary();
            summary.setKey(prefix + "/object" + i);
            summaries.add(summary);
        }

        return summaries;
    }
}
