// Feeds raw ANSI output through xterm.js's terminal emulator (headless build) and writes
// the resulting cells as a static HTML page styled like a terminal window.
// usage: node render.mjs <in.ansi> <out.html> <profile> <title>
import fs from 'node:fs';
import xterm from '@xterm/headless';

const { Terminal } = xterm;

// Terminal profiles: background, foreground and the 16 color ANSI palette.
const PROFILES = {
	// A neutral modern dark terminal.
	dark: {
		bg: '#15171c', fg: '#d6d9df', bar: '#1f2128', title: '#8a8f99',
		ansi: ['#1b1d23', '#f0626b', '#7ccf7a', '#e6c06a', '#5aa6f5', '#c88cf0', '#4fc6d6', '#d6d9df',
			'#5c6370', '#ff7a82', '#93e090', '#f5d27f', '#77b8ff', '#d9a3ff', '#6fd8e6', '#ffffff'],
	},
	one_dark: {
		bg: '#282c34', fg: '#abb2bf', bar: '#21252b', title: '#7f848e',
		ansi: ['#282c34', '#e06c75', '#98c379', '#e5c07b', '#61afef', '#c678dd', '#56b6c2', '#abb2bf',
			'#5c6370', '#e06c75', '#98c379', '#e5c07b', '#61afef', '#c678dd', '#56b6c2', '#ffffff'],
	},
	darcula: {
		bg: '#2b2b2b', fg: '#a9b7c6', bar: '#3c3f41', title: '#8c8c8c',
		ansi: ['#2b2b2b', '#ff6b68', '#a8c023', '#d6bf55', '#5394ec', '#ae8abe', '#299999', '#a9b7c6',
			'#606366', '#ff8785', '#a8c023', '#ffff00', '#7eaef1', '#ff99ff', '#6cdada', '#ffffff'],
	},
	light: {
		bg: '#fbfbfa', fg: '#383a42', bar: '#ececeb', title: '#7a7d85',
		ansi: ['#383a42', '#d23b3b', '#3f8f2f', '#b07a00', '#2f6fd6', '#a626a4', '#0f8a99', '#d8d8d8',
			'#a0a1a7', '#e45649', '#50a14f', '#c18401', '#4078f2', '#b33fb1', '#0184bc', '#ffffff'],
	},
};

const [input, output, profileName, title] = process.argv.slice(2);
const profile = PROFILES[profileName];
const data = fs.readFileSync(input, 'utf8').replace(/\n$/, '');
const lines = data.split('\n');
const cols = Math.max(...lines.map((l) => l.replace(/\x1b\[[0-9;]*m/g, '').length));
const term = new Terminal({ cols, rows: lines.length, convertEol: true, allowProposedApi: true });

function hex(n) {
	return '#' + n.toString(16).padStart(6, '0');
}

function esc(s) {
	return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

term.write(data, () => {
	const buffer = term.buffer.active;
	const rows = [];
	for (let y = 0; y < lines.length; y++) {
		const line = buffer.getLine(y);
		let html = '';
		let run = null;
		let text = '';
		const flush = () => {
			if (text) {
				html += run ? `<span style="${run}">${esc(text)}</span>` : esc(text);
			}
			text = '';
		};
		for (let x = 0; x < cols; x++) {
			const cell = line.getCell(x);
			let fg = null;
			if (cell.isFgRGB()) {
				fg = hex(cell.getFgColor());
			}
			else if (cell.isFgPalette()) {
				let i = cell.getFgColor();
				// Bold brightens the first eight colors, as most terminals do by default.
				if (cell.isBold() && i < 8) {
					i += 8;
				}
				fg = profile.ansi[i];
			}
			const styles = [];
			if (fg) styles.push(`color:${fg}`);
			if (cell.isBold()) styles.push('font-weight:700');
			if (cell.isDim()) styles.push('opacity:.6');
			if (cell.isItalic()) styles.push('font-style:italic');
			const style = styles.join(';') || null;
			if (style !== run) {
				flush();
				run = style;
			}
			text += cell.getChars() || ' ';
		}
		flush();
		rows.push(html.replace(/\s+$/, ''));
	}
	const page = `<!doctype html><meta charset="utf-8"><style>
html,body{margin:0;background:${profile.bg}}
body{zoom:2}
.win{display:inline-block;background:${profile.bg};min-width:100%}
.bar{height:34px;background:${profile.bar};display:flex;align-items:center;position:relative}
.dots{display:flex;gap:8px;padding-left:14px}
.dots i{width:12px;height:12px;border-radius:50%;display:block}
.title{position:absolute;left:0;right:0;text-align:center;font:500 12.5px "JetBrainsMono Nerd Font Mono",monospace;color:${profile.title}}
pre{margin:0;padding:16px 20px 18px;font:400 14px/1.5 "JetBrainsMono Nerd Font Mono",monospace;color:${profile.fg};font-variant-ligatures:none}
</style><div class="win" id="win"><div class="bar"><div class="dots"><i style="background:#ff5f57"></i><i style="background:#febc2e"></i><i style="background:#28c840"></i></div><div class="title">${esc(title)}</div></div><pre>${rows.join('\n')}</pre></div>`;
	fs.writeFileSync(output, page);
	// Width and height in CSS pixels: 0.6em advance at 14px, 21px line height.
	const width = 2 * (Math.ceil(cols * 8.4) + 44);
	const height = 2 * (34 + lines.length * 21 + 34);
	console.log(`${width} ${height}`);
});
