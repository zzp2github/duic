/**
 * Copyright 2017-2018 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zhudy.duic.repository.impl

import io.zhudy.duic.domain.Server
import io.zhudy.duic.repository.AbstractTransactionRepository
import io.zhudy.duic.repository.ServerRepository
import org.joda.time.DateTime
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * @author Kevin Zou (kevinz@weghst.com)
 */
open class PostgreSQLServerRepository(
        transactionManager: PlatformTransactionManager,
        private val jdbcTemplate: NamedParameterJdbcTemplate
) : ServerRepository, AbstractTransactionRepository(transactionManager) {

    override fun register(host: String, port: Int) = Mono.create<Int> {
        val n = transactionTemplate.execute {
            jdbcTemplate.update(
                    "INSERT INTO server(id,host,port,init_at,active_at) VALUES(:id,:host,:port,now(),now()) ON CONFLICT (id) DO UPDATE SET init_at=now(),active_at=now()",
                    mapOf(
                            "id" to "${host}_$port",
                            "host" to host,
                            "port" to port
                    )
            )
        }
        it.success(n)
    }

    override fun unregister(host: String, port: Int) = Mono.create<Int> {
        val n = transactionTemplate.execute {
            jdbcTemplate.update("DELETE FROM server WHERE id=:id", mapOf("id" to "${host}_$port"))
        }
        it.success(n)
    }

    override fun ping(host: String, port: Int) = Mono.create<Int> {
        val n = transactionTemplate.execute {
            jdbcTemplate.update(
                    "UPDATE server SET active_at=:active_at WHERE id=:id",
                    mapOf(
                            "id" to "${host}_$port",
                            "active_at" to Date()
                    )
            )
        }
        it.success(n)
    }

    override fun findServers() = Flux.create<Server> { sink ->
        roTransactionTemplate.execute {
            jdbcTemplate.query(
                    "SELECT host,port,init_at,active_at FROM server WHERE active_at >= :active_at",
                    mapOf(
                            "active_at" to DateTime.now().minusMinutes(ServerRepository.ACTIVE_TIMEOUT_MINUTES).toDate()
                    )
            ) {
                sink.next(Server(
                        host = it.getString("host"),
                        port = it.getInt("port"),
                        initAt = DateTime(it.getTimestamp("init_at")),
                        activeAt = DateTime(it.getTimestamp("active_at"))
                ))
            }
        }
        sink.complete()
    }

    override fun clean() = Mono.create<Int> {
        val n = transactionTemplate.execute {
            jdbcTemplate.update(
                    "DELETE FROM server WHERE active_at<=:active_at",
                    mapOf(
                            "active_at" to DateTime.now().minusMinutes(ServerRepository.CLEAN_BEFORE_MINUTES).toDate()
                    )
            )
        }
        it.success(n)
    }
}