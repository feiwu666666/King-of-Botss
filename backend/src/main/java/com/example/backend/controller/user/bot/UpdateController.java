package com.example.backend.controller.user.bot;

import com.example.backend.service.user.bot.UpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UpdateController {
    @Autowired
    private UpdateService updateService;

    public Map<String,String> update(@RequestParam Map<String,String> data){
        return updateService.update(data);

    }

}
