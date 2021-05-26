package io.talken.dex.api;

import io.talken.common.Bootstrap;
import io.talken.common.CommonConsts;
import io.talken.common.DefaultApplication;
import io.talken.dex.api.config.auth.AccessTokenInterceptor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;

/**
 * The type Api launcher.
 */
@SpringBootApplication(scanBasePackages = {"io.talken.dex.api"}, exclude = {ErrorMvcAutoConfiguration.class})
public class ApiLauncher extends DefaultApplication {
    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
		// Parse arguments
		for(int i = 0; i < args.length; i++) {
			// force uid
			if(args[i].equals("-u")) {
				i++;
				if(args.length <= i) usage();
				try {
					AccessTokenInterceptor.setAuthSkipper(Long.valueOf(args[i]));
				} catch(Exception ex) {
					usage();
					Bootstrap.shutdown(CommonConsts.EXITCODE.ARGUMENT_ERROR);
				}
			}
		}
		Bootstrap.startup(ApiLauncher.class, args);
	}

	private static void usage() {
		System.out.println("Colligence API Server");
		System.out.println("Usage :");
		System.out.println("  -p : profile [(l)ocal,(d)ev,(t)est,(p)roduction]");
		System.out.println("  -v : verbose mode");
		System.out.println("  -u : skip auth with given uid");
	}
}
