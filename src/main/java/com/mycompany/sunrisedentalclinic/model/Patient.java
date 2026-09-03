package com.mycompany.sunrisedentalclinic.model;

public record Patient(int id, String fullName, String gender, String address, String contact, String email) {

    @Override
    public String toString() {
        return fullName + " (" + contact + ")";
    }
}
