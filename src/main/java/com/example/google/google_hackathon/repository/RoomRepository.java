package com.example.google.google_hackathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
    //List<Room> findByOwner(AppUser user);
    List<Room> findAll();

    Room findByIndex(String index);

    List<Room> findByParentIsNull();

    List<Room> findByParent(Room parent);
}
