package com.neobank.module.integrations.idprovider;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class IdVerificationClient {

    private final Random random = new Random();

    public Integer verifyPassport() {
        return simulateConfidence();
    }

    public Integer verifyNationalId() {
        return simulateConfidence();
    }

    public Integer verifyDrivingLicense() {
        return simulateConfidence();
    }

    private Integer simulateConfidence() {
        boolean networkConnected = random.nextInt(4) < 3;
        if (!networkConnected) {
            return -1;
        }

        return switch (random.nextInt(3)) {
            case 0 -> random.nextInt(61);
            case 1 -> 61 + random.nextInt(31);
            default -> 92 + random.nextInt(9);
        };
    }
}