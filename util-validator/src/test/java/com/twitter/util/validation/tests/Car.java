package com.twitter.util.validation.tests;

import java.util.Collections;
import java.util.List;

import com.twitter.util.validation.constraints.ValidPassengerCount;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidPassengerCount(max = 2)
public class Car {
  @NotNull
  private final String manufacturer;

  @NotNull
  @Size(min = 2, max = 14)
  private final String licensePlate;

  @Min(2)
  private final int seatCount;

  private List<Person> passengers;

  public Car(String manufacturer, String licencePlate, int seatCount) {
    this.manufacturer = manufacturer;
    this.licensePlate = licencePlate;
    this.seatCount = seatCount;
    this.passengers = Collections.emptyList();
  }

  public void load(List<Person> passengers) {
    this.passengers = passengers;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public String getLicensePlate() {
    return licensePlate;
  }

  public int getSeatCount() {
    return seatCount;
  }

  public List<Person> getPassengers() {
    return passengers;
  }
}
