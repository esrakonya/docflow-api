package io.docflow.api.web.dashboard;

import com.stripe.exception.StripeException;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.billing.service.BillingService;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.infrastructure.exception.PaymentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
  * Session-authenticated (browser) equivalent of {@link io.docflow.api.core.billing.controller.BillingController}.
  * <p>
  * Same reasoning as {@link DashboardApiKeyController}: {@code /api/v1/billing/**} lives on
  * the stateless, {@code X-API-KEY}-only filter chain, so the dashboard talks to
  * {@link BillingService} directly instead of calling that REST endpoint.
  */
@Controller
@RequestMapping("/dashboard/billing")
@RequiredArgsConstructor
@Slf4j
public class DashboardBillingController {

    private final BillingService billingService;
    private final PlanRepository planRepository;

    @GetMapping
    public String showBillingPage(@AuthenticationPrincipal ApiClientDto currentClient, Model model) {
        List<Plan> upgradeOptions = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .filter(plan -> plan.getStripePriceId() != null)
                .filter(plan -> !plan.getName().equalsIgnoreCase(currentClient.getPlanName()))
                .toList();

        model.addAttribute("companyName", currentClient.getCompanyName());
        model.addAttribute("currentPlan", currentClient.getPlanName());
        model.addAttribute("upgradeOptions", upgradeOptions);

        model.addAttribute("hasBillingAccount", !PlanTier.FREE.equalsIgnoreCase(currentClient.getPlanName()));
        model.addAttribute("hasBillingAccount", currentClient.getStripeCustomerId() != null);
        return "dashboard/billing";
    }

    @PostMapping(value = "/upgrade", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String upgrade(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @RequestParam String targetPlan,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String checkoutUrl = billingService.createCheckoutSession(currentClient, targetPlan);
            return "redirect:" + checkoutUrl;
        } catch (StripeException | PaymentProcessingException e) {
            log.warn("Checkout session creation failed for client {}: {}", currentClient.getId(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Could not start checkout: " + e.getMessage());
            return "redirect:/dashboard/billing";
        }
    }

    @PostMapping(value = "/portal", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String portal(
            @AuthenticationPrincipal ApiClientDto currentClient,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String portalUrl = billingService.createPortalSession(currentClient);
            return "redirect:" + portalUrl;
        } catch (StripeException | PaymentProcessingException e) {
            log.warn("Portal session creation failed for client {}: {}", currentClient.getId(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Could not open billing portal: " + e.getMessage());
            return "redirect:/dashboard/billing";
        }
    }
}
