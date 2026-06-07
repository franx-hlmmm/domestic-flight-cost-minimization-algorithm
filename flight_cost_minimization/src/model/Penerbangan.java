/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import java.time.LocalTime;

/**
 *
 * @author Farras Hilmy Zaidan
 */

public class Penerbangan {
    public String asal;
    public String tujuan;
    public String maskapai;
    public LocalTime waktuBerangkat;
    public LocalTime waktuTiba;
    public int harga;

    public Penerbangan(String asal, String tujuan, String maskapai, String waktuBerangkat, String waktuTiba, int harga) {
        this.asal = asal;
        this.tujuan = tujuan;
        this.maskapai = maskapai;
        this.waktuBerangkat = LocalTime.parse(waktuBerangkat);
        this.waktuTiba = LocalTime.parse(waktuTiba);
        this.harga = harga;
    }
}
