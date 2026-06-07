/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Farras Hilmy Zaidan
 */

public class MysqlConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/db_penerbangan_asa";
    private static final String USER = "root";
    private static final String PASS = "!U20n06D04i24P!";

    public List<Penerbangan> getPenerbanganByTanggal(String tanggal) {
        List<Penerbangan> listPenerbangan = new ArrayList<>();
        String query = "SELECT asal, tujuan, maskapai, waktu_berangkat, waktu_tiba, harga FROM penerbangan WHERE tanggal_penerbangan = ?";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, tanggal);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                listPenerbangan.add(new Penerbangan(
                    rs.getString("asal"),
                    rs.getString("tujuan"),
                    rs.getString("maskapai"),
                    rs.getString("waktu_berangkat").replace(".", ":"), 
                    rs.getString("waktu_tiba").replace(".", ":"),
                    rs.getInt("harga")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error Database: " + e.getMessage());
        }
        return listPenerbangan;
    }
}