package com.alaya.protocol.core.filters;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alaya.protocol.ObjectMapperFactory;
import com.alaya.protocol.Web3j;
import com.alaya.protocol.Web3jService;
import com.alaya.protocol.core.Request;
import com.alaya.protocol.core.methods.response.PlatonFilter;
import com.alaya.protocol.core.methods.response.PlatonLog;
import com.alaya.protocol.core.methods.response.PlatonUninstallFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import rx.Observable;
import rx.Subscription;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class FilterTester {

    private Web3jService web3jService;
    Web3j web3j;

    final ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    final ScheduledExecutorService scheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    @Before
    public void setUp() {
        web3jService = mock(Web3jService.class);
        web3j = Web3j.build(web3jService, 1000, scheduledExecutorService);
    }

    <T> void runTest(PlatonLog ethLog, Observable<T> observable) throws Exception {
        PlatonFilter ethFilter = objectMapper.readValue(
                "{\n"
                        + "  \"id\":1,\n"
                        + "  \"jsonrpc\": \"2.0\",\n"
                        + "  \"result\": \"0x1\"\n"
                        + "}", PlatonFilter.class);

        PlatonUninstallFilter ethUninstallFilter = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}", PlatonUninstallFilter.class);

        PlatonLog notFoundFilter = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,"
                + "\"error\":{\"code\":-32000,\"message\":\"filter not found\"}}",
                PlatonLog.class);

        @SuppressWarnings("unchecked")
        List<T> expected = createExpected(ethLog);
        Set<T> results = Collections.synchronizedSet(new HashSet<T>());

        CountDownLatch transactionLatch = new CountDownLatch(expected.size());

        CountDownLatch completedLatch = new CountDownLatch(1);

        when(web3jService.send(any(Request.class), eq(PlatonFilter.class)))
                .thenReturn(ethFilter);
        when(web3jService.send(any(Request.class), eq(PlatonLog.class)))
            .thenReturn(ethLog).thenReturn(notFoundFilter).thenReturn(ethLog);
        when(web3jService.send(any(Request.class), eq(PlatonUninstallFilter.class)))
                .thenReturn(ethUninstallFilter);

        Subscription subscription = observable.subscribe(
                result -> {
                    results.add(result);
                    transactionLatch.countDown();
                },
                throwable -> fail(throwable.getMessage()),
                () -> completedLatch.countDown());

        transactionLatch.await(1, TimeUnit.SECONDS);
        assertThat(results, equalTo(new HashSet<>(expected)));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    List createExpected(PlatonLog ethLog) {
        List<PlatonLog.LogResult> logResults = ethLog.getLogs();
        if (logResults.isEmpty()) {
            fail("Results cannot be empty");
        }

        return ethLog.getLogs().stream()
                .map(t -> t.get()).collect(Collectors.toList());
    }
}