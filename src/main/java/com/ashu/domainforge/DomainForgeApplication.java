package com.ashu.domainforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DomainForgeApplication {
  public static void main(String[] args) {
    SpringApplication.run(DomainForgeApplication.class, args);
  }
}
