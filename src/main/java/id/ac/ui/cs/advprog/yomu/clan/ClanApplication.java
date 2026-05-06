package id.ac.ui.cs.advprog.yomu.clan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "id.ac.ui.cs.advprog.yomu.clan",
    "id.ac.ui.cs.advprog.yomu.shared"
})
public class ClanApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClanApplication.class, args);
    }
}
