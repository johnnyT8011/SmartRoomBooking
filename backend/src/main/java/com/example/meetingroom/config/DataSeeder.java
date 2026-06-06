package com.example.meetingroom.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;

/**
 * Seeds the demo master data used by the front-end prototype: rooms 會議室 A/B/C and
 * users 牛 / 豬 / 羊 / 鴕鳥. Runs once on startup if the tables are empty.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MeetingRoomRepository meetingRoomRepository;

    public DataSeeder(UserRepository userRepository, MeetingRoomRepository meetingRoomRepository) {
        this.userRepository = userRepository;
        this.meetingRoomRepository = meetingRoomRepository;
    }

    @Override
    public void run(String... args) {
        if (meetingRoomRepository.count() == 0) {
            meetingRoomRepository.saveAll(List.of(
                    new MeetingRoom("會議室 A"),
                    new MeetingRoom("會議室 B"),
                    new MeetingRoom("會議室 C")));
        }
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                    new User("牛", "niu@example.com"),
                    new User("豬", "zhu@example.com"),
                    new User("羊", "yang@example.com"),
                    new User("鴕鳥", "ostrich@example.com")));
        }
    }
}
