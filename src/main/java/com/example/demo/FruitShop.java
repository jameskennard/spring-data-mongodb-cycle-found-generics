package com.example.demo;

import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class FruitShop {

    public Selection<FruitBasket> fruitBaskets;

}
