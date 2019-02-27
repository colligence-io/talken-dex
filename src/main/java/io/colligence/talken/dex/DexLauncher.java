package io.colligence.talken.dex;

import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.config.auth.AccessTokenInterceptor;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = {"io.colligence.talken.dex"}, exclude = {ErrorMvcAutoConfiguration.class})
public class DexLauncher {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DexLauncher.class);

	private static ConfigurableApplicationContext applicationContext;

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(DexLauncher.class);

		boolean verbose = false;

		// Parse arguments
		for(int i = 0; i < args.length; i++) {
			// Verbose Mode
			if(args[i].equals("-v")) verbose = true;

			// force uid
			if(args[i].equals("-u")) {
				i++;
				if(args.length <= i) usage();
				try {
					AccessTokenInterceptor.setAuthSkipper(Long.valueOf(args[i]));
				} catch(Exception ex) {
					usage();
				}
			}

			// Choose profile
			if(args[i].equals("-p")) {
				i++;
				if(args.length <= i) usage();

				boolean profile_found = false;
				for(CommonConsts.RUNNING_PROFILE _p : CommonConsts.RUNNING_PROFILE.values()) {
					if(_p.getName().startsWith(args[i])) {
						RunningProfile.setRunningProfile(_p);
						profile_found = true;
					}
				}
				if(!profile_found) {
					System.out.println("Cannot recognize profile name : " + args[i]);
					shutdown(CommonConsts.EXITCODE.ARGUMENT_ERROR);
				}
			}
		}

		application.setBannerMode(Banner.Mode.OFF);
		// Verbose Mode
		// Enable Hibernate SQL out
		// Enable stackTrace Logging
		if(verbose) {
			application.setAdditionalProfiles("common", RunningProfile.getRunningProfile().getName(), "verbose");
			PrefixedLogger.setVerboseMode();
		} else {
			application.setAdditionalProfiles("common", RunningProfile.getRunningProfile().getName());
		}

		//application.
		applicationContext = application.run(args);
	}

	private static void usage() {
		System.out.println("Colligence API Server");
		System.out.println("Usage :");
		System.out.println("  -p : profile [(l)ocal,(d)ev,(t)est,(p)roduction]");
		System.out.println("  -v : verbose mode");
		System.out.println("  -u : skip auth with given uid");
		shutdown(CommonConsts.EXITCODE.ARGUMENT_ERROR);
	}

	public static void shutdown(CommonConsts.EXITCODE code) {
		if(applicationContext != null) {
			logger.error("Shutdown : {} ({})", code.name(), code.getCode());
			System.exit(SpringApplication.exit(applicationContext));
		} else {
			System.exit(code.getCode());
		}
	}
}
