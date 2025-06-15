package com.example.google.google_hackathon.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.dto.RoomDTO;
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

    // public List<RoomDTO> getRoomDTOsForUser(String username) {
    //     AppUser user = AppUserRepository.findByUsername(username)
    //                     .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    //     List<Room> rooms = roomRepository.findByOwner(user);
    //     return rooms.stream().map(RoomDTO::new).collect(Collectors.toList());
    // }

    public List<RoomDTO> getAllRooms() {
    return roomRepository.findAll().stream()
        .map(RoomDTO::new)
        .collect(Collectors.toList());
    }


    public Room createRoom(String title, String parentIndex, String username) {
        AppUser user = AppUserRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Room room = new Room();
        room.setTitle(title);
        room.setOwner(user);
        
        Room parent = null;
        if (parentIndex != null) {
            parent = roomRepository.findByIndex(parentIndex);
            room.setParent(parent);
        }


        String newIndex = generateNextIndex(parent);
        room.setIndex(newIndex);

        return roomRepository.save(room);
    }

    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }

    // private String generateIndex(String parentIndex) {
    //     // 例: "2-1" → "2-1-3"
    //     // 実装は必要に応じて
    //     Long maxIndex = roomRepository.findMaxIndexAsLong(); 
    //     //return UUID.randomUUID().toString(); // 仮
    //     return String.valueOf((maxIndex != null ? maxIndex + 1 : 1));
    // }

    public String generateNextIndex(Room parent) {
    if (parent == null) {
        // ルートの場合：最大の index を探して +1
        List<Room> rootRooms = roomRepository.findByParentIsNull();
        int max = rootRooms.stream()
            .map(Room::getIndex)
            .filter(index -> index.matches("\\d+"))
            .mapToInt(Integer::parseInt)
            .max()
            .orElse(0);
        return String.valueOf(max + 1);
    } else {
        // 子の場合：親の index をベースに、同じ親を持つ子の最大末尾番号を探す
        List<Room> siblings = roomRepository.findByParent(parent);
        int max = siblings.stream()
            .map(Room::getIndex)
            .map(index -> index.replaceFirst(parent.getIndex() + "-", ""))
            .filter(suffix -> suffix.matches("\\d+"))
            .mapToInt(Integer::parseInt)
            .max()
            .orElse(0);
        return parent.getIndex() + "-" + (max + 1);
    }
}

}
