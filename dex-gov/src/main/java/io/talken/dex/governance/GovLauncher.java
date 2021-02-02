package io.talken.dex.governance;

import io.talken.common.Bootstrap;
import io.talken.common.DefaultApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;

@SpringBootApplication(scanBasePackages = {"io.talken.dex.governance"}, exclude = {ErrorMvcAutoConfiguration.class})
@EnableMBeanExport(registration= RegistrationPolicy.IGNORE_EXISTING)
public class GovLauncher extends DefaultApplication {
	public static void main(String[] args) {
		Bootstrap.startup(GovLauncher.class, args);
	}
}
