package per.misaka.misakanetworks

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [ReactiveUserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class MisakanetworksApplication

fun main(args: Array<String>) {
    runApplication<MisakanetworksApplication>(*args)
}
