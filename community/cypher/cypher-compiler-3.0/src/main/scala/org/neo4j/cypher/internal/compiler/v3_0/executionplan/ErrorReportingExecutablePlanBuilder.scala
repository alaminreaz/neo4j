/*
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_0.executionplan

import org.neo4j.cypher.internal.compiler.v3_0.planner.CantHandleQueryException
import org.neo4j.cypher.internal.compiler.v3_0.spi.PlanContext
import org.neo4j.cypher.internal.compiler.v3_0.{CompilationPhaseTracer, PreparedQuery}
import org.neo4j.cypher.internal.frontend.v3_0.InvalidArgumentException

case class ErrorReportingExecutablePlanBuilder(inner: ExecutablePlanBuilder) extends ExecutablePlanBuilder {
  override def producePlan(inputQuery: PreparedQuery, planContext: PlanContext, tracer: CompilationPhaseTracer): Either[CompiledPlan, PipeInfo] =
    try {
      inner.producePlan(inputQuery, planContext, tracer)
    } catch {
      case e: CantHandleQueryException =>
        throw new InvalidArgumentException("The given query is not currently supported in the selected cost-based planner", e)
    }
}