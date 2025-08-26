package com.pcelica.model;

import java.time.LocalDate;
import java.util.Objects;

public class BeeUser {
    private String id;
    private String firstName;
    private String lastName;
    private String gender;
    private LocalDate birthDate;
    private String birthPlace;
    private String residenceCity;
    private int colonies;
    private String docNumber;
    private int seqNumber;
    private int year;

    public BeeUser() {}

    // getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String birthPlace) { this.birthPlace = birthPlace; }

    public String getResidenceCity() { return residenceCity; }
    public void setResidenceCity(String residenceCity) { this.residenceCity = residenceCity; }

    public int getColonies() { return colonies; }
    public void setColonies(int colonies) { this.colonies = colonies; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public int getSeqNumber() { return seqNumber; }
    public void setSeqNumber(int seqNumber) { this.seqNumber = seqNumber; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BeeUser)) return false;
        BeeUser beeUser = (BeeUser) o;
        return Objects.equals(id, beeUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
