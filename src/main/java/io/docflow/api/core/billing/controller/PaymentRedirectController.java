package io.docflow.api.core.billing.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PaymentRedirectController {

    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam Map<String, String> allParams) {
        return "SUCCESS_CONFIRMED: Your plan has been upgraded. Params received: " + allParams.size();
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel(@RequestParam Map<String, String> allParams) {
        return "CANCEL_CONFIRMED: No changes were made.";
    }
}
