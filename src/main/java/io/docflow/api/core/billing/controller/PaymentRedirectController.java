package io.docflow.api.core.billing.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
  * Renders the pages Stripe Checkout redirects the browser back to
  * ({@code docflow.billing.stripe.success-url} / {@code cancel-url}).
  * <p>
  * This runs on the stateless, permit-all {@code /payment/**} matcher (see
  * {@code SecurityConfig#adminFilterChain}), so it cannot rely on the dashboard
  * session being read here — it only renders a static confirmation and links
  * back into {@code /dashboard/**}, which re-authenticates normally off the
  * browser's existing session cookie.
  * <p>
  * The actual plan upgrade is applied asynchronously by
  * {@code StripeEventHandlerService} via the {@code checkout.session.completed}
  * webhook, not by this page — the page may render slightly before that webhook
  * has been processed, which the copy below accounts for.
  */
@Controller
public class PaymentRedirectController {

    @GetMapping("/payment/success")
    public String paymentSuccess() {
        return "payment/success";
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel() {
        return "payment/cancel";
    }
}
