package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.SQLException;

@FunctionalInterface
interface SqlTask {
    void run() throws SQLException;
}
