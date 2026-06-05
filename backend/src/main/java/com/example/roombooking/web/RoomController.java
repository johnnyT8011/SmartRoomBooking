package com.example.roombooking.web;

import com.example.roombooking.model.Room;
import com.example.roombooking.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final BookingService service;

    public RoomController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<Room> rooms() {
        return service.getRooms();
    }
}
