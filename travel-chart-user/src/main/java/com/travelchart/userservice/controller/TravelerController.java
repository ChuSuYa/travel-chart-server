package com.travelchart.userservice.controller;

import com.travelchart.common.model.Result;
import com.travelchart.userservice.dto.TravelerDTO;
import com.travelchart.userservice.entity.Traveler;
import com.travelchart.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/traveler")
@RequiredArgsConstructor
public class TravelerController {

    private final UserService userService;

    @GetMapping("/list")
    public Result<List<Traveler>> list(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(userService.listTravelers(userId));
    }

    @PostMapping("/add")
    public Result<Traveler> add(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody TravelerDTO dto) {
        return Result.ok(userService.addTraveler(userId, dto));
    }

    @PutMapping("/update")
    public Result<Traveler> update(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody TravelerDTO dto) {
        return Result.ok(userService.updateTraveler(userId, dto));
    }

    @DeleteMapping("/remove/{travelerId}")
    public Result<Void> remove(@RequestHeader("X-User-Id") Long userId, @PathVariable Long travelerId) {
        userService.removeTraveler(userId, travelerId);
        return Result.ok();
    }
}
