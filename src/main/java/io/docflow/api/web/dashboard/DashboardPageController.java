package io.docflow.api.web.dashboard;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.service.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardPageController {

    private final DashboardSessionService dashboardSessionService;
    private final UsageService usageService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "dashboard/login";
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String handleLoginForm(@RequestParam String apiKey, HttpServletRequest httpRequest, Model model) {
        Optional<ApiClientDto> clientDto = dashboardSessionService.login(apiKey, httpRequest);

        if (clientDto.isEmpty()) {
            model.addAttribute("error", "Invalid or inactive API key.");
            return "dashboard/login";
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/logout-page")
    public String handleLogoutForm(HttpServletRequest httpRequest) {
        dashboardSessionService.logout(httpRequest);
        return "redirect:/dashboard/login";
    }

    @GetMapping
    public String showDashboardHome(@AuthenticationPrincipal ApiClientDto currentClient, Model model) {
        model.addAttribute("usage", usageService.getCurrentUsage(currentClient));
        model.addAttribute("companyName", currentClient.getCompanyName());
        return "dashboard/index";
    }
}
