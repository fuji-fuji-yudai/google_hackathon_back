package com.example.google.google_hackathon.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Room;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.RoomRepository;

@Service
public class RoomService {
    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AppUserRepository AppUserRepository;

    public List<Room> getRoomsForUser(String username) {
        AppUser user = AppUserRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return roomRepository.findByOwner(user);
    }

    public Room createRoom(String title, String parentIndex, String username) {
        AppUser user = AppUserRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Room room = new Room();
        room.setTitle(title);
        room.setOwner(user);
        room.setIndex(generateIndex(parentIndex));
        if (parentIndex != null) {
            Room parent = roomRepository.findByIndex(parentIndex);
            room.setParent(parent);
        }
        return roomRepository.save(room);
    }

    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }

    private String generateIndex(String parentIndex) {
        // 例: "2-1" → "2-1-3"
        // 実装は必要に応じて
        return UUID.randomUUID().toString(); // 仮
    }
}
