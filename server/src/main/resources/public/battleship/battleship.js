// Battleship frontend: three screens (setup -> placement -> battle) driven by
// the /api/battleship/ endpoints. All game rules live server-side; the only
// logic duplicated here is placement fit-checking, so the ghost preview and
// local randomize work without a network round-trip per hover.

"use strict";

const API = "/api/battleship/";

async function apiCall(path, body) {
  const resp = await fetch(API + path, body === undefined ? {} : {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!resp.ok) throw new Error(await resp.text());
  return resp.json();
}

const $ = (id) => document.getElementById(id);
const coordStr = (x, y) => `${x + 1},${String.fromCharCode(65 + y)}`;

let game = null;      // latest state view from the server
let gameId = null;
let locked = false;   // input lock while a shot is in flight

// ---------------------------------------------------------------------------
// Boards: build a size x size grid with a label gutter; returns cell elements.
// ---------------------------------------------------------------------------

function buildBoard(container, size, clickable) {
  container.innerHTML = "";
  container.classList.toggle("clickable", clickable);
  container.style.setProperty("--tile", size <= 6 ? "52px" : size <= 9 ? "44px" : "36px");
  container.style.gridTemplateColumns = `repeat(${size + 1}, var(--tile))`;
  const cells = [];
  container.appendChild(label(""));
  for (let x = 0; x < size; x++) container.appendChild(label(String(x + 1)));
  for (let y = 0; y < size; y++) {
    container.appendChild(label(String.fromCharCode(65 + y)));
    for (let x = 0; x < size; x++) {
      const c = document.createElement("div");
      c.className = "cell";
      c.dataset.x = x;
      c.dataset.y = y;
      (cells[x] ??= [])[y] = c;
      container.appendChild(c);
    }
  }
  return cells;

  function label(text) {
    const l = document.createElement("div");
    l.className = "lbl";
    l.textContent = text;
    return l;
  }
}

// ---------------------------------------------------------------------------
// Screen switching
// ---------------------------------------------------------------------------

function show(screen) {
  for (const s of document.querySelectorAll(".screen")) s.hidden = true;
  $(screen).hidden = false;
}

// ---------------------------------------------------------------------------
// Screen 1: setup + leaderboard
// ---------------------------------------------------------------------------

function scoreRows(rows) {
  return rows.map((r, i) => `
    <tr><td class="rank">${i + 1}</td><td>${r.initials ?? "---"}</td>
    <td class="score">${r.score}</td><td class="dim">${r.boardSize}x${r.boardSize}</td>
    <td class="dim">${r.opponent}</td></tr>`).join("");
}

async function loadLeaderboard() {
  const rows = await apiCall("leaderboard");
  $("leaderboard").querySelector("tbody").innerHTML = scoreRows(rows);
  $("no-scores").hidden = rows.length > 0;
}

$("deploy").addEventListener("click", async () => {
  const boardSize = Number(document.querySelector('input[name="size"]:checked').value);
  const opponent = document.querySelector('input[name="opponent"]:checked').value;
  game = await apiCall("game", { boardSize, opponent, cheat: $("cheat").checked });
  gameId = game.gameId;
  startPlacement();
});

// ---------------------------------------------------------------------------
// Screen 2: placement (local state; submitted in one POST on START BATTLE)
// ---------------------------------------------------------------------------

const place = { cells: null, grid: null, fleet: [], idx: 0, horizontal: true, placements: [] };

function startPlacement() {
  place.fleet = game.yourFleet;
  place.idx = 0;
  place.horizontal = true;
  place.placements = [];
  place.grid = Array.from({ length: game.size }, () => Array(game.size).fill(null));
  place.cells = buildBoard($("place-board"), game.size, true);
  for (const row of place.cells) {
    for (const cell of row) {
      cell.addEventListener("mouseenter", () => ghost(cell, true));
      cell.addEventListener("mouseleave", () => ghost(cell, false));
      cell.addEventListener("click", () => placeShip(Number(cell.dataset.x), Number(cell.dataset.y)));
    }
  }
  $("start-battle").disabled = true;
  placePrompt();
  show("screen-place");
}

function currentShip() {
  return place.idx < place.fleet.length ? place.fleet[place.idx] : null;
}

function shipCells(x, y, len, horizontal) {
  const cells = [];
  for (let i = 0; i < len; i++) cells.push([horizontal ? x + i : x, horizontal ? y : y + i]);
  return cells;
}

function fits(x, y, len, horizontal) {
  return shipCells(x, y, len, horizontal).every(
    ([cx, cy]) => cx < game.size && cy < game.size && !place.grid[cx][cy]);
}

function ghost(cell, on) {
  for (const row of place.cells) for (const c of row) c.classList.remove("ghost-ok", "ghost-bad");
  const ship = currentShip();
  if (!on || !ship) return;
  const x = Number(cell.dataset.x), y = Number(cell.dataset.y);
  const ok = fits(x, y, ship.length, place.horizontal);
  for (const [cx, cy] of shipCells(x, y, ship.length, place.horizontal)) {
    if (cx < game.size && cy < game.size) place.cells[cx][cy].classList.add(ok ? "ghost-ok" : "ghost-bad");
  }
}

function placeShip(x, y) {
  const ship = currentShip();
  if (!ship || !fits(x, y, ship.length, place.horizontal)) return;
  for (const [cx, cy] of shipCells(x, y, ship.length, place.horizontal)) {
    place.grid[cx][cy] = ship.icon;
    place.cells[cx][cy].classList.add("ship", "spent");
  }
  place.placements.push({ x, y, horizontal: place.horizontal });
  place.idx++;
  $("start-battle").disabled = currentShip() !== null;
  placePrompt();
}

function placePrompt() {
  const ship = currentShip();
  $("place-prompt").textContent = ship
    ? `Placing: ${ship.name}\nLength: ${ship.length}\nOrientation: ${place.horizontal ? "HORIZONTAL" : "VERTICAL"}\n\nClick a starting cell (R to rotate).`
    : "Fleet ready! Hit START BATTLE when you're set.";
}

function clearPlacement() {
  place.idx = 0;
  place.placements = [];
  place.grid = place.grid.map((row) => row.map(() => null));
  for (const row of place.cells) for (const c of row) c.className = "cell";
  $("start-battle").disabled = true;
  placePrompt();
}

function randomizePlacement() {
  clearPlacement();
  outer: for (let restart = 0; restart < 50; restart++) {
    for (const ship of place.fleet) {
      let placed = false;
      for (let tries = 0; tries < 500 && !placed; tries++) {
        const horizontal = Math.random() < 0.5;
        const x = Math.floor(Math.random() * game.size);
        const y = Math.floor(Math.random() * game.size);
        if (fits(x, y, ship.length, horizontal)) {
          place.horizontal = horizontal;
          placeShip(x, y);
          placed = true;
        }
      }
      if (!placed) { clearPlacement(); continue outer; }
    }
    return;
  }
}

$("rotate").addEventListener("click", () => { place.horizontal = !place.horizontal; placePrompt(); });
document.addEventListener("keydown", (e) => {
  if (e.key.toLowerCase() === "r" && !$("screen-place").hidden) {
    place.horizontal = !place.horizontal;
    placePrompt();
  }
});
$("clear").addEventListener("click", clearPlacement);
$("randomize").addEventListener("click", randomizePlacement);
$("start-battle").addEventListener("click", async () => {
  game = await apiCall("place", { gameId, ships: place.placements });
  startBattle();
});

// ---------------------------------------------------------------------------
// Screen 3: battle
// ---------------------------------------------------------------------------

const battle = { yours: null, enemy: null };

function startBattle() {
  battle.yours = buildBoard($("your-board"), game.size, false);
  battle.enemy = buildBoard($("enemy-board"), game.size, true);
  for (const row of battle.enemy) {
    for (const cell of row) {
      cell.addEventListener("click", () => fire(Number(cell.dataset.x), Number(cell.dataset.y)));
    }
  }
  locked = false;
  $("log-you").textContent = "Fire at the enemy waters.";
  $("log-ai").textContent = "Waiting for your first shot...";
  banner("YOUR TURN — fire at the enemy waters", "neon-cyan");
  renderState();
  show("screen-battle");
}

function banner(text, cls, thinking = false) {
  const b = $("banner");
  b.textContent = text;
  b.className = cls + (thinking ? " thinking" : "");
}

// Redraw both boards and the stats panel from the server's state view.
function renderState() {
  const sunkIcons = new Set(game.yourFleet.filter((s) => s.sunk).map((s) => s.icon));
  const yourShipAt = {};
  for (const s of game.yourBoard.ships) yourShipAt[`${s.x},${s.y}`] = s.icon;

  paint(battle.yours, (cell, x, y) => {
    const icon = yourShipAt[`${x},${y}`];
    if (icon) cell.classList.add("ship");
    const shot = game.yourBoard.shots.find((s) => s.x === x && s.y === y);
    if (shot) {
      cell.classList.add("spent", shot.hit ? "hit" : "miss");
      if (shot.hit && sunkIcons.has(icon)) cell.classList.add("sunk");
    }
  });

  const enemySunk = new Set(game.enemyBoard.sunkShips.map((s) => `${s.x},${s.y}`));
  const enemyShipAt = new Set(game.enemyBoard.ships.map((s) => `${s.x},${s.y}`));
  paint(battle.enemy, (cell, x, y) => {
    if (enemyShipAt.has(`${x},${y}`)) cell.classList.add("ship");
    const shot = game.enemyBoard.shots.find((s) => s.x === x && s.y === y);
    if (shot) {
      cell.classList.add("spent", shot.hit ? "hit" : "miss");
      if (enemySunk.has(`${x},${y}`)) cell.classList.add("sunk");
    }
  });

  const fleetSize = game.yourFleet.length;
  const alive = (fleet) => fleet.filter((s) => !s.sunk).length;
  const streak = game.streakFactor > 1 ? `  (streak x${game.streakFactor})` : "";
  $("stat-score").textContent = `Score: ${game.score}${streak}`;
  $("stat-yours").textContent = `Your ships:   ${alive(game.yourFleet)} / ${fleetSize}`;
  $("stat-enemy").textContent = `Enemy ships:  ${alive(game.enemyFleet)} / ${fleetSize}`;
  $("stat-accuracy").textContent = `Your accuracy: ${game.accuracy}%`;

  function paint(cells, fn) {
    for (let x = 0; x < game.size; x++) {
      for (let y = 0; y < game.size; y++) {
        cells[x][y].className = "cell";
        fn(cells[x][y], x, y);
      }
    }
  }
}

async function fire(x, y) {
  if (locked || game.phase !== "BATTLE") return;
  if (game.enemyBoard.shots.some((s) => s.x === x && s.y === y)) {
    $("log-you").textContent = `You already fired at ${coordStr(x, y)}.`;
    return;
  }
  locked = true;
  banner("ENEMY AI IS THINKING…", "neon-magenta", true);
  try {
    const resp = await apiCall("fire", { gameId, x, y });
    game = resp.state;
    renderState();

    const p = resp.playerShot;
    const hitText = p.result === "MISS" ? "miss."
      : p.result === "SUNK" ? `you SANK their ${p.shipName}!` : `hit their ${p.shipName}!`;
    const pts = resp.points > 0 ? `  [+${resp.points}]` : "";
    $("log-you").textContent = `You fired at ${coordStr(x, y)} - ${hitText}${pts}`;
    if (resp.points > 0) floatPoints(battle.enemy[x][y], resp.points);

    const a = resp.aiShot;
    if (a) {
      const timing = a.millis >= 0 ? ` (${a.millis}ms)` : "";
      $("log-ai").textContent = `AI [${a.source}] fired at ${coordStr(a.x, a.y)} - ${a.result}${timing}`;
    }

    if (game.phase === "FINISHED") {
      endGame();
    } else {
      banner("YOUR TURN — fire at the enemy waters", "neon-cyan");
      locked = false;
    }
  } catch (err) {
    $("log-you").textContent = String(err.message || err);
    banner("YOUR TURN — fire at the enemy waters", "neon-cyan");
    locked = false;
  }
}

function floatPoints(cell, points) {
  const rect = cell.getBoundingClientRect();
  const pop = document.createElement("div");
  pop.className = "popup";
  pop.textContent = `+${points}`;
  pop.style.left = `${rect.left + rect.width / 2 - 20}px`;
  pop.style.top = `${rect.top - 8}px`;
  $("popups").appendChild(pop);
  setTimeout(() => pop.remove(), 900);
}

// ---------------------------------------------------------------------------
// End of game: overlay, initials, leaderboard
// ---------------------------------------------------------------------------

async function endGame() {
  const won = game.playerWon;
  banner(won ? "VICTORY" : "DEFEAT", won ? "neon-green" : "neon-red");
  $("end-title").textContent = won ? "VICTORY!" : "DEFEAT";
  $("end-title").className = won ? "neon-green" : "neon-red";
  $("end-sub").textContent = won ? "You sank the entire enemy fleet." : "The enemy sank your entire fleet.";
  $("end-score").textContent = `FINAL SCORE: ${game.score}`;
  $("end-bonus").textContent = game.endBonus > 0 ? `(end-of-game bonus +${game.endBonus})` : "";
  $("initials-entry").hidden = !(game.qualifiesForLeaderboard && !game.initialsSubmitted);
  $("end-board").hidden = true;
  $("overlay").hidden = false;
  if ($("initials-entry").hidden) showEndLeaderboard();
  else $("initials").focus();
}

async function showEndLeaderboard() {
  const rows = await apiCall("leaderboard");
  if (rows.length > 0) {
    $("end-leaderboard").querySelector("tbody").innerHTML = scoreRows(rows);
    $("end-board").hidden = false;
  }
}

$("submit-initials").addEventListener("click", submitInitials);
$("initials").addEventListener("keydown", (e) => { if (e.key === "Enter") submitInitials(); });

async function submitInitials() {
  const initials = $("initials").value.trim().toUpperCase();
  if (!/^[A-Z0-9]{1,3}$/.test(initials)) { $("initials").focus(); return; }
  try {
    await apiCall("initials", { gameId, initials });
    $("initials-entry").hidden = true;
    showEndLeaderboard();
  } catch (err) {
    $("initials-entry").hidden = true;
    showEndLeaderboard();
  }
}

$("play-again").addEventListener("click", () => location.reload());

// ---------------------------------------------------------------------------

loadLeaderboard();
show("screen-setup");
