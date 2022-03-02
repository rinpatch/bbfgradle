// Auto-generated by org.jetbrains.kotlin.generators.tests.GenerateRangesCodegenTestData. DO NOT EDIT!
// WITH_STDLIB



fun box(): String {
    val list1 = ArrayList<UInt>()
    val range1 = (8u downTo 3u step 2).reversed()
    for (i in range1) {
        list1.add(i)
        if (list1.size > 23) break
    }
    if (list1 != listOf<UInt>(4u, 6u, 8u)) {
        return "Wrong elements for (8u downTo 3u step 2).reversed(): $list1"
    }

    val list2 = ArrayList<UInt>()
    val range2 = (8u.toUByte() downTo 3u.toUByte() step 2).reversed()
    for (i in range2) {
        list2.add(i)
        if (list2.size > 23) break
    }
    if (list2 != listOf<UInt>(4u, 6u, 8u)) {
        return "Wrong elements for (8u.toUByte() downTo 3u.toUByte() step 2).reversed(): $list2"
    }

    val list3 = ArrayList<UInt>()
    val range3 = (8u.toUShort() downTo 3u.toUShort() step 2).reversed()
    for (i in range3) {
        list3.add(i)
        if (list3.size > 23) break
    }
    if (list3 != listOf<UInt>(4u, 6u, 8u)) {
        return "Wrong elements for (8u.toUShort() downTo 3u.toUShort() step 2).reversed(): $list3"
    }

    val list4 = ArrayList<ULong>()
    val range4 = (8uL downTo 3uL step 2L).reversed()
    for (i in range4) {
        list4.add(i)
        if (list4.size > 23) break
    }
    if (list4 != listOf<ULong>(4u, 6u, 8u)) {
        return "Wrong elements for (8uL downTo 3uL step 2L).reversed(): $list4"
    }

    return "OK"
}
