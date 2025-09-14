package com.lgk.lgkaicodeservice.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class Video implements Serializable {
    private String type;
    private String author;
    private String arcurl;
    private String title;
    private String description;
    private String pic;
    private int play;
    private int videoReview;
    private String tag;
    private String duration;
}
