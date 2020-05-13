package no.ssb.subsetsservice;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping("/hello")
    public String hello() {
        return
                "<pre> _  ___                _____ _____    _____       _              _\n" +
                "| |/ / |        /\\    / ____/ ____|  / ____|     | |            | |\n" +
                "| ' /| |       /  \\  | (___| (___   | (___  _   _| |__  ___  ___| |_ ___\n" +
                "|  < | |      / /\\ \\  \\___ \\\\___ \\   \\___ \\| | | | '_ \\/ __|/ _ \\ __/ __|\n" +
                "| . \\| |____ / ____ \\ ____) |___) |  ____) | |_| | |_) \\__ \\  __/ |_\\__ \\\n" +
                "|_|\\_\\______/_/    \\_\\_____/_____/  |_____/ \\__,_|_.__/|___/\\___|\\__|___/\n</pre>";
    }
}
