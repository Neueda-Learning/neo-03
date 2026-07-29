package com.neobank.module.integrations.idprovider;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IdVerificationClientTest {

    @Test
    void returnsMinusOneWhenTheNetworkIsDown() throws Exception {
        IdVerificationClient client = clientWithRandomSequence(3, 3, 3);

        assertThat(client.verifyPassport()).isEqualTo(-1);
        assertThat(client.verifyNationalId()).isEqualTo(-1);
        assertThat(client.verifyDrivingLicense()).isEqualTo(-1);
    }

    @Test
    void returnsALowConfidenceScore() throws Exception {
        IdVerificationClient client = clientWithRandomSequence(0, 0, 42);

        assertThat(client.verifyPassport()).isEqualTo(42);
    }

    @Test
    void returnsAMediumConfidenceScore() throws Exception {
        IdVerificationClient client = clientWithRandomSequence(0, 1, 11);

        assertThat(client.verifyPassport()).isEqualTo(72);
    }

    @Test
    void returnsAHighConfidenceScore() throws Exception {
        IdVerificationClient client = clientWithRandomSequence(0, 2, 4);

        assertThat(client.verifyPassport()).isEqualTo(96);
    }

    private static IdVerificationClient clientWithRandomSequence(int... values) throws Exception {
        IdVerificationClient client = new IdVerificationClient();
        Field randomField = IdVerificationClient.class.getDeclaredField("random");
        randomField.setAccessible(true);
        randomField.set(client, new StubRandom(values));
        return client;
    }

    private static final class StubRandom extends Random {
        private final int[] values;
        private int index;

        private StubRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[index++];
        }
    }
}
