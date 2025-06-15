package com.example.google.google_hackathon.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms",schema = "public")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String index; // 例: "2-1-1"
    private String title;

    @ManyToOne
    private AppUser owner;

    @ManyToOne
    private Room parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Room> children = new ArrayList<>();

    // ゲッターとセッター

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser user) {
        this.owner = user;
    }

    public Room getParent() {
        return parent;
    }

    public void setParent(Room parent) {
        this.parent = parent;
    }

    public List<Room> getChildren() {
        return children;
    }

    public void setChildren(List<Room> children) {
        this.children = children;
    }
}

