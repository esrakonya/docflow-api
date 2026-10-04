package io.docflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
class StripeConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StripeProperties.class, StripeConfig.class);

    @Test
    void stripeNotConfigured_doesNotFailContextStartup() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StripeProperties.class);
                    assertThat(context).hasSingleBean(StripeConfig.class);
                });
    }

    @Test
    void stripeConfigured_initializesSuccessfully() {
        contextRunner
                .withPropertyValues(
                        "docflow.billing.stripe.secret-key=sk_test_test_purpose_only",
                        "docflow.billing.stripe.webhook-secret=whsec_test_purpose_only",
                        "docflow.billing.stripe.success-url=http://test-server/payment/success",
                        "docflow.billing.stripe.cancel-url=http://test-server/payment/cancel"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    StripeProperties properties = context.getBean(StripeProperties.class);

                    assertThat(properties.isConfigured()).isTrue();
                    assertThat(properties.getSecretKey()).isEqualTo("sk_test_test_purpose_only");
                    assertThat(properties.getWebhookSecret()).isEqualTo("whsec_test_purpose_only");
                });
    }

    @Test
    void stripePartiallyConfigured_isNotConsideredConfigured() {
        contextRunner
                .withPropertyValues(
                        "docflow.billing.stripe.secret-key=sk_test_test_purpose_only"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    StripeProperties properties = context.getBean(StripeProperties.class);

                    assertThat(properties.isConfigured()).isFalse();
                });
    }
}
