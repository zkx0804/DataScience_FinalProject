package edu.unh.cs980

import org.mapdb.DBMaker
import org.mapdb.Serializer


/**
 * Class: KotlinDatabase
 * Description: Used to open a MapDB database. Shared between classes.
 */
class KotlinDatabase(dbLocation: String) {
    val db = DBMaker.fileDB(dbLocation)
            .fileMmapEnable()
            .closeOnJvmShutdown()
            .make()

    val parMap = db.hashMap("par_map", Serializer.STRING, Serializer.STRING).createOrOpen()
    val entityMap = db.hashMap("entity_map", Serializer.STRING, Serializer.STRING).createOrOpen()
    val e2eDistMap = db.hashMap("e2e_dist", Serializer.STRING, Serializer.STRING).createOrOpen()
    val p2eDistMap = db.hashMap("p2e_dist", Serializer.STRING, Serializer.STRING).createOrOpen()
    val weightMap = db.hashMap("weight_map", Serializer.STRING, Serializer.DOUBLE).createOrOpen()
}