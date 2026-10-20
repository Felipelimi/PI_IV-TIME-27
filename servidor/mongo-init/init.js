// Estrutura do banco local, igual à do Atlas.
// Roda só na primeira vez que o container do Mongo sobe (volume vazio).
// Depois de alterar este arquivo: docker compose down -v && docker compose up

db = db.getSiblingDB("easyagenda");

db.createCollection("usuarios", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["nome", "email", "senha", "tipo"],
      properties: {
        nome: { bsonType: "string" },
        email: { bsonType: "string" },
        senha: { bsonType: "string", description: "guardar o hash, nunca a senha pura" },
        tipo: { enum: ["professor", "aluno"] }
      }
    }
  }
});
db.usuarios.createIndex({ email: 1 }, { name: "email_1", unique: true });

db.createCollection("horarios_livres", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["professorId", "dia", "horaInicio", "horaFim", "modalidade"],
      properties: {
        professorId: { bsonType: "objectId" },
        dia: { bsonType: "string", description: "ex.: 2026-10-20" },
        horaInicio: { bsonType: "string", description: "ex.: 14:00" },
        horaFim: { bsonType: "string", description: "ex.: 15:00" },
        modalidade: { enum: ["presencial", "online"] }
      }
    }
  }
});

db.createCollection("aulas", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["professorId", "alunoId", "dataHora", "status", "modalidade"],
      properties: {
        professorId: { bsonType: "objectId" },
        alunoId: { bsonType: "objectId" },
        dataHora: { bsonType: "date" },
        status: { enum: ["agendada", "realizada", "cancelada"] },
        modalidade: { enum: ["presencial", "online"] }
      }
    }
  }
});
// Um professor não pode ter duas aulas ativas no mesmo horário (canceladas não contam)
db.aulas.createIndex(
  { professorId: 1, dataHora: 1 },
  {
    name: "professor_horario_unico",
    unique: true,
    partialFilterExpression: { status: { $in: ["agendada", "realizada"] } }
  }
);
