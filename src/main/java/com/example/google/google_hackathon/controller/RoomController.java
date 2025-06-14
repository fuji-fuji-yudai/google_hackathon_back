package com.example.google.google_hackathon.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.google.google_hackathon.dto.RoomRequest;
import com.example.google.google_hackathon.entity.Room;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import com.example.google.google_hackathon.service.RoomService;

@RestController
@RequestMapping("/chat/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public List<Room> getRooms(@RequestHeader("Authorization") String authHeader) {
    String token = authHeader.replace("Bearer ", "");
    String username = jwtTokenProvider.getUsernameFromToken(token);
    return roomService.getRoomsForUser(username);
}


    @PostMapping
    public Room createRoom(@RequestBody RoomRequest request, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtTokenProvider.getUsernameFromToken(token);
        return roomService.createRoom(request.getTitle(), request.getParentIndex(), username);
    }

    @DeleteMapping("/{id}")
    public void deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
    }
}
