package com.bestduo_BE;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BestduoBeApplication {

	public static void main(String[] args) {
		// .env 파일을 시스템 프로퍼티로 로드 (로컬 개발용, Railway 등에서는 환경 변수 사용)
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();
		dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));

		SpringApplication.run(BestduoBeApplication.class, args);
	}

}
