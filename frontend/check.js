const fs = require('fs');
const content = fs.readFileSync('./src/app/page.tsx', 'utf8');

// We'll just try to parse it with Babel.
// We don't have babel installed, let's just write a script to check bracket matching.
function checkMatchingBrackets(str) {
    let stack = [];
    let lines = str.split('\n');
    for (let i = 0; i < lines.length; i++) {
        let line = lines[i];
        for (let j = 0; j < line.length; j++) {
            let char = line[j];
            if (char === '{' || char === '(' || char === '<') stack.push({char, line: i + 1});
            if (char === '}' || char === ')' || char === '>') {
                let last = stack.pop();
                if (!last) {
                    console.log('Unmatched ' + char + ' at line ' + (i + 1));
                    return;
                }
                if (char === '}' && last.char !== '{') console.log('Mismatch ' + last.char + ' and ' + char + ' at line ' + (i + 1));
                if (char === ')' && last.char !== '(') console.log('Mismatch ' + last.char + ' and ' + char + ' at line ' + (i + 1));
                if (char === '>' && last.char !== '<') console.log('Mismatch ' + last.char + ' and ' + char + ' at line ' + (i + 1));
            }
        }
    }
    console.log('Stack size:', stack.length);
}

checkMatchingBrackets(content);
