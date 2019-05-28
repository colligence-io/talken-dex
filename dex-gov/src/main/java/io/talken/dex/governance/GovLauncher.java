package io.talken.dex.governance;

import io.talken.common.Bootstrap;
import io.talken.common.DefaultApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"io.talken.dex.governance"}, exclude = {ErrorMvcAutoConfiguration.class})
public class GovLauncher extends DefaultApplication {
	public static void main(String[] args) {
		Bootstrap.startup(GovLauncher.class, args);
	}
}
