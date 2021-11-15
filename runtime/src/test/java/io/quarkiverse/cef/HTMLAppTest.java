package io.quarkiverse.cef;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HTMLAppTest {
    @Inject
    HTMLApp htmlApp;

    public void testEnsureSafe() {
    }
}
